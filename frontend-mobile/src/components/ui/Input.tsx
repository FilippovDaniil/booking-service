import { type InputHTMLAttributes, forwardRef } from 'react';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, Props>(
  ({ label, error, className = '', ...props }, ref) => (
    <div className="flex flex-col gap-1">
      {label && <label className="text-sm font-medium text-gray-700">{label}</label>}
      <input
        ref={ref}
        className={[
          'w-full rounded-xl border px-4 py-3 text-sm outline-none transition-colors',
          'border-gray-200 bg-gray-50 text-gray-900 placeholder-gray-400',
          'focus:border-blue-500 focus:bg-white focus:ring-2 focus:ring-blue-100',
          error ? 'border-red-400 focus:border-red-500 focus:ring-red-100' : '',
          className,
        ].join(' ')}
        {...props}
      />
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  )
);

Input.displayName = 'Input';
