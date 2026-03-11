import './DialBufferDisplay.css';

interface Props {
  buffer: string;
  visible: boolean;
}

export default function DialBufferDisplay({ buffer, visible }: Props) {
  if (!visible || !buffer) return null;

  return (
    <div className="dial-buffer">
      <span className="dial-buffer-text">{buffer}</span>
    </div>
  );
}
