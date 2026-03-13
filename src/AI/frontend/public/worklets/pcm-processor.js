class PcmProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0];
    if (!input || input.length === 0) {
      return true;
    }

    const channel = input[0];
    if (!channel) {
      return true;
    }

    const frame = new Float32Array(channel);
    this.port.postMessage(frame.buffer, [frame.buffer]);
    return true;
  }
}

registerProcessor("pcm-processor", PcmProcessor);
