import { type ButtonHTMLAttributes } from 'react';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  fullWidth?: boolean;
}

const variants = {
  primary: 'bg-blue-600 text-white active:bg-blue-700 disabled:bg-blue-300',
  secondary: 'bg-gray-100 text-gray-800 active:bg-gray-200',
  ghost: 'bg-transparent text-blue-600 active:bg-blue-50',
  danger: 'bg-red-500 text-white active:bg-red-600 disabled:bg-red-300',
};

const sizes = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2.5 text-sm',
  lg: 'px-4 py-3.5 text-base',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  fullWidth,
  children,
  className = '',
  disabled,
  ...props
}: Props) {
  return (
    <button
      disabled={disabled || loading}
      className={[
        'rounded-xl font-medium transition-colors select-none',
        variants[variant],
        sizes[size],
        fullWidth ? 'w-full' : '',
        'disabled:opacity-60 disabled:cursor-not-allowed',
        className,
      ].join(' ')}
      {...props}
    >
      {loading ? (
        <span className="flex items-center justify-center gap-2">
          <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
          Загрузка...
        </span>
      ) : children}
    </button>
  );
}
