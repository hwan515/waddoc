import './DialBufferDisplay.css';
import { formatPhoneNumber } from '../../utils/phoneNumber';

interface Props {
  buffer: string;
  visible: boolean;
}

export default function DialBufferDisplay({ buffer, visible }: Props) {
  if (!visible || !buffer) return null;

  return (
    <div className="dial-buffer">
      <span className="dial-buffer-text">{formatPhoneNumber(buffer)}</span>
    </div>
  );
}
