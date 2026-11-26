#ifndef PIPELINEQNN_HPP
#define PIPELINEQNN_HPP

#include <memory>
#include <string>
#include <vector>

#include "Pipeline.hpp"
#include "QnnModel.hpp"
#include "QnnRuntime.hpp"

// Shared base for the QNN-backed formats (sd15npu, sdxl): owns the three
// stage models and the cfg=1 uncond-skip capability common to per-half UNet
// execution.
class PipelineQnn : public Pipeline {
 public:
  using Pipeline::Pipeline;

 protected:
  bool canSkipUncond() const override { return true; }

  // Long prompts on a 77-token UNet graph: evaluate the UNet once per
  // 77-token chunk of `hidden` that holds prompt text and average the noise
  // predictions (each chunk is a self-contained CLIP encoding, so this is
  // the composable-diffusion mean over the chunks). `run(chunk_hidden, out)`
  // executes one 77-token pass. A single chunk runs straight into `out`.
  template <typename RunFn>
  static void runChunkAveraged(int chunks, int hidden_dim, float *hidden,
                               float *out, size_t out_count, RunFn &&run) {
    if (chunks <= 1) {
      run(hidden, out);
      return;
    }
    std::vector<float> acc(out_count, 0.0f), tmp(out_count);
    for (int c = 0; c < chunks; ++c) {
      run(hidden + (size_t)c * 77 * hidden_dim, tmp.data());
      for (size_t k = 0; k < out_count; ++k) acc[k] += tmp[k];
    }
    const float inv = 1.0f / (float)chunks;
    for (size_t k = 0; k < out_count; ++k) out[k] = acc[k] * inv;
  }

  std::unique_ptr<QnnModel> unet_;
  std::unique_ptr<QnnModel> vae_decoder_;
  std::unique_ptr<QnnModel> vae_encoder_;
};

#endif  // PIPELINEQNN_HPP
