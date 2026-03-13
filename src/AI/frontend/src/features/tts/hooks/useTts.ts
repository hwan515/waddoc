import { useState } from "react";

interface SpeakOptions {
  onEnd?: () => void;
  onError?: () => void;
}

export function useTts() {
  const [isSpeaking, setIsSpeaking] = useState(false);

  function speak(text: string, options?: SpeakOptions) {
    if (!("speechSynthesis" in window)) {
      options?.onError?.();
      return;
    }

    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ko-KR";
    utterance.onstart = () => setIsSpeaking(true);
    utterance.onend = () => {
      setIsSpeaking(false);
      options?.onEnd?.();
    };
    utterance.onerror = () => {
      setIsSpeaking(false);
      options?.onError?.();
    };

    window.speechSynthesis.speak(utterance);
  }

  function stop() {
    window.speechSynthesis.cancel();
    setIsSpeaking(false);
  }

  return {
    isSpeaking,
    speak,
    stop
  };
}
