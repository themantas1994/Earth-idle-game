import { BuyQuantity } from './TechCard';

const OPTIONS: BuyQuantity[] = [1, 10, 100, 'max'];

export default function QuantitySelector({ value, onChange }: { value: BuyQuantity; onChange: (v: BuyQuantity) => void }) {
  return (
    <div className="qty-selector">
      {OPTIONS.map((opt) => (
        <button
          key={String(opt)}
          className={`qty-btn ${value === opt ? 'qty-btn--active' : ''}`}
          onClick={() => onChange(opt)}
        >
          {opt === 'max' ? 'Max' : `×${opt}`}
        </button>
      ))}
    </div>
  );
}
