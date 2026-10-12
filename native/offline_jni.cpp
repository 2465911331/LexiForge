#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::atomic<bool> stopped{false};
std::mutex inference_mutex;
std::once_flag backend_once;
bool abort_work(void *) { return stopped.load(); }
bool load_progress(float, void *) { return !stopped.load(); }
void emit(JNIEnv *env, jobject listener, jmethodID method, const std::string &text) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    if (!bytes) throw std::runtime_error("Insufficient memory");
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    env->CallVoidMethod(listener, method, bytes);
    env->DeleteLocalRef(bytes);
    if (env->ExceptionCheck()) throw std::runtime_error("Output callback failed");
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_offline_NativeLlama_begin(JNIEnv *, jobject) { stopped.store(false); }
extern "C" JNIEXPORT void JNICALL
Java_com_example_offline_NativeLlama_cancel(JNIEnv *, jobject) { stopped.store(true); }

extern "C" JNIEXPORT void JNICALL
Java_com_example_offline_NativeLlama_generate(JNIEnv *env, jobject, jstring path, jbyteArray input,
                                            jint token_limit, jobject listener) {
    // A second screen cannot load another copy of the model while the first is stopping.
    std::unique_lock<std::mutex> lock(inference_mutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Model is busy; please retry");
        return;
    }
    try {
        if (stopped.load()) return;
        std::call_once(backend_once, [] { llama_backend_init(); });
        const char *raw_path = env->GetStringUTFChars(path, nullptr);
        if (!raw_path) return;
        std::string file_path(raw_path);
        env->ReleaseStringUTFChars(path, raw_path);
        std::string prompt(static_cast<size_t>(env->GetArrayLength(input)), '\0');
        env->GetByteArrayRegion(input, 0, static_cast<jsize>(prompt.size()), reinterpret_cast<jbyte *>(prompt.data()));
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.progress_callback = load_progress;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(llama_model_load_from_file(file_path.c_str(), mp), llama_model_free);
        if (stopped.load()) return;
        if (!model) throw std::runtime_error("Unable to load bundled model");
        auto cp = llama_context_default_params();
        cp.n_ctx = 2048;
        cp.n_batch = 256;
        cp.n_ubatch = 256;
        cp.n_threads = 4;
        cp.n_threads_batch = 4;
        cp.abort_callback = abort_work;
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
        if (!ctx) throw std::runtime_error("Unable to allocate model context");
        const auto *vocab = llama_model_get_vocab(model.get());
        int count = -llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), nullptr, 0, false, true);
        if (count <= 0 || count + token_limit >= 2048) throw std::runtime_error("Prompt exceeds context limit");
        std::vector<llama_token> tokens(count);
        llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), tokens.data(), count, false, true);
        for (int start = 0; start < count; start += 256) {
            if (stopped.load()) return;
            int result = llama_decode(ctx.get(), llama_batch_get_one(tokens.data() + start, std::min(256, count - start)));
            if (stopped.load()) return;
            if (result) throw std::runtime_error("Model prompt evaluation failed");
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.1f, 0.0f, 0.0f));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.95f, 1));
        // Official MiniCPM5 non-thinking settings: temperature .7, top_p .95, min_p 0.
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        jclass listener_class = env->GetObjectClass(listener);
        jmethodID method = env->GetMethodID(listener_class, "onText", "([B)V");
        env->DeleteLocalRef(listener_class);
        if (!method) return;
        std::string output;
        for (int i = 0; i < std::clamp(static_cast<int>(token_limit), 1, 256) && !stopped.load(); ++i) {
            llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            std::vector<char> piece(64);
            int length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
            if (length < 0) {
                piece.resize(-length);
                length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
            }
            if (length > 0) output.append(piece.data(), length);
            emit(env, listener, method, output);
            int result = llama_decode(ctx.get(), llama_batch_get_one(&token, 1));
            if (stopped.load()) return;
            if (result) throw std::runtime_error("Model generation failed");
        }
        // RAII releases context, sampler and mmap on success, cancellation and errors.
    } catch (const std::exception &e) {
        if (!env->ExceptionCheck() && !stopped.load())
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
    }
}
