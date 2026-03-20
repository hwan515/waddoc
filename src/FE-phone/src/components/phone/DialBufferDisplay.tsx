import './DialBufferDisplay.css';

interface Props {
  buffer: string;
  visible: boolean;
}

export default function DialBufferDisplay({ buffer, visible }: Props) {
  if (!visible || !buffer) return null;

  const formattedBuffer =
    buffer.length > 3 ? `${buffer.slice(0, 3)} ${buffer.slice(3)}` : buffer;

  return (
    <div className="dial-buffer">
      <span className="dial-buffer-text">{formattedBuffer}</span>
    </div>
  );
}
