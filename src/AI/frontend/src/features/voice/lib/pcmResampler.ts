export function downsampleToRate(
  input: Float32Array,
  inputRate: number,
  outputRate: number
) {
  if (inputRate === outputRate) {
    return input;
  }

  const ratio = inputRate / outputRate;
  const outputLength = Math.round(input.length / ratio);
  const output = new Float32Array(outputLength);
  let position = 0;

  for (let outputIndex = 0; outputIndex < outputLength; outputIndex += 1) {
    const leftIndex = Math.floor(position);
    const rightIndex = Math.min(leftIndex + 1, input.length - 1);
    const interpolation = position - leftIndex;

    output[outputIndex] =
      input[leftIndex] * (1 - interpolation) + input[rightIndex] * interpolation;
    position += ratio;
  }

  return output;
}

export function toInt16Pcm(float32: Float32Array) {
  const pcm = new Int16Array(float32.length);
  for (let index = 0; index < float32.length; index += 1) {
    const clamped = Math.max(-1, Math.min(1, float32[index]));
    pcm[index] = clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff;
  }

  return pcm;
}
