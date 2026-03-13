import { downsampleToRate, toInt16Pcm } from "./pcmResampler";

export interface AudioCaptureHandle {
  stop(): Promise<void>;
}

interface CreateAudioCaptureOptions {
  targetSampleRate: number;
  onChunk: (pcmChunk: Int16Array) => void;
}

export async function createAudioCapture(
  options: CreateAudioCaptureOptions
): Promise<AudioCaptureHandle> {
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      channelCount: 1,
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
      sampleRate: options.targetSampleRate,
      sampleSize: 16
    }
  });

  const audioContext = new AudioContext();
  await audioContext.resume();
  await audioContext.audioWorklet.addModule("/worklets/pcm-processor.js");

  const source = audioContext.createMediaStreamSource(stream);
  const node = new AudioWorkletNode(audioContext, "pcm-processor");
  const sink = audioContext.createGain();
  sink.gain.value = 0;

  node.port.onmessage = (event) => {
    const frame = new Float32Array(event.data as ArrayBuffer);
    const resampled = downsampleToRate(frame, audioContext.sampleRate, options.targetSampleRate);
    options.onChunk(toInt16Pcm(resampled));
  };

  source.connect(node);
  node.connect(sink);
  sink.connect(audioContext.destination);

  return {
    async stop() {
      node.disconnect();
      source.disconnect();
      sink.disconnect();
      stream.getTracks().forEach((track) => track.stop());
      await audioContext.close();
    }
  };
}
