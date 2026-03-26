import React from 'react';

/**
 * 공통 버튼 컴포넌트 (버튼 테마, 크기, 너비를 props로 제어)
 * 
 * @param {string} children 버튼 내부 텍스트
 * @param {string} variant 'primary' | 'secondary' | 'outline' | 'danger'
 * @param {string} size 'small' | 'medium' | 'large'
 * @param {boolean} fullWidth 가로 길이를 100%로 할지 여부
 */
const Button = ({ 
    children, 
    onClick, 
    type = 'button', 
    variant = 'primary', 
    size = 'medium', 
    fullWidth = false, 
    className = '',
    disabled = false,
    ...props 
}) => {
    
    // 기본 스타일 세팅
    const baseStyles = "inline-flex items-center justify-center font-bold rounded-xl transition-all shadow-sm";
    
    // 크기 변형
    const sizeStyles = {
        small: "px-3 py-1.5 text-xs",
        medium: "px-4 py-2 text-sm",
        large: "px-4 py-4 text-base",
    };

    // 테마 색상 변형
    const variantStyles = {
        primary: "bg-primary hover:bg-accent-1 text-white shadow-lg shadow-primary/30 transform hover:-translate-y-0.5",
        secondary: "bg-secondary hover:bg-secondary/80 text-dark",
        outline: "bg-transparent border-2 border-slate-200 text-slate-700 hover:bg-slate-50",
        danger: "bg-red-500 hover:bg-red-600 text-white",
    };

    // 버튼 너비 제어
    const widthStyle = fullWidth ? "w-full" : "";

    // 커스텀 클래스 병합
    const combinedClasses = `${baseStyles} ${sizeStyles[size]} ${variantStyles[variant]} ${widthStyle} ${className} ${disabled ? 'opacity-50 cursor-not-allowed transform-none shadow-none' : ''}`;

    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled}
            className={combinedClasses}
            {...props}
        >
            {children}
        </button>
    );
};

export default Button;
