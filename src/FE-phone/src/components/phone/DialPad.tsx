import { playDtmfTone } from '../../utils/dtmfSound';
import './DialPad.css';

interface DialPadProps {
  onDigit: (digit: string) => void;
  disabled?: boolean;
}

const KEYS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['*', '0', '#'],
];

const SUB_LABELS: Record<string, string> = {
  '1': '',
  '2': 'ABC',
  '3': 'DEF',
  '4': 'GHI',
  '5': 'JKL',
  '6': 'MNO',
  '7': 'PQRS',
  '8': 'TUV',
  '9': 'WXYZ',
  '*': '',
  '0': '+',
  '#': '',
};

export default function DialPad({ onDigit, disabled }: DialPadProps) {
  const handlePress = (key: string) => {
    if (disabled) return;
    playDtmfTone(key);
    onDigit(key);
  };

  return (
    <div className="dialpad">
      {KEYS.map((row, ri) => (
        <div className="dialpad-row" key={ri}>
          {row.map((key) => (
            <button
              key={key}
              className="dialpad-btn"
              onClick={() => handlePress(key)}
              disabled={disabled}
            >
              <span className="dialpad-btn-main">{key}</span>
              {SUB_LABELS[key] && (
                <span className="dialpad-btn-sub">{SUB_LABELS[key]}</span>
              )}
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
