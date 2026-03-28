const MAX_PHONE_LENGTH = 20;

export function normalizePhoneDigits(value: string): string {
  return value.replace(/\D/g, '');
}

export function appendPhoneDigit(currentValue: string, input: string): string {
  const nextDigit = normalizePhoneDigits(input);
  if (!nextDigit) {
    return normalizePhoneDigits(currentValue);
  }

  return `${normalizePhoneDigits(currentValue)}${nextDigit}`.slice(0, MAX_PHONE_LENGTH);
}

export function formatPhoneNumber(value: string): string {
  const digits = normalizePhoneDigits(value);

  if (digits.length <= 3) {
    return digits;
  }

  if (digits.length <= 7) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  }

  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}
