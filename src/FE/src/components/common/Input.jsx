import React, { forwardRef } from 'react';

/**
 * 공통 폼 입력 컴포넌트 (아이콘, 에러 메시지 지원)
 *
 * @param {string} label 입력 폼 상단에 표출될 라벨 텍스트
 * @param {string} type input 타입 (text, password, email 등)
 * @param {string} errorMessage 유효성 검사 실패 시 표출될 에러 메시지
 * @param {ReactNode} icon 오른쪽에 들어갈 아이콘 (ex: EyeOff 등)
 */
const Input = forwardRef(({
    label,
    id,
    type = "text",
    name,
    value,
    onChange,
    placeholder,
    required = false,
    className = '',
    iconLeft = null,
    iconRight = null,
    errorMessage = '',
    ...props
}, ref) => {

    const hasError = errorMessage.length > 0;

    return (
        <div className="w-full">
            {label && (
                <label htmlFor={id || name} className="block text-sm font-bold text-slate-800 mb-2">
                    {label}
                </label>
            )}
            
            <div className="relative">
                {iconLeft && (
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                        {iconLeft}
                    </div>
                )}
                
                <input
                    ref={ref}
                    id={id || name}
                    name={name}
                    type={type}
                    value={value}
                    onChange={onChange}
                    required={required}
                    placeholder={placeholder}
                    className={`
                        w-full py-3.5 border rounded-xl text-slate-900 placeholder-slate-400 
                        focus:outline-none focus:ring-2 focus:border-transparent transition-all sm:text-sm font-medium
                        ${hasError ? 'border-red-500 focus:ring-red-500' : 'border-slate-200 focus:ring-[#0353A4]'}
                        ${iconLeft ? 'pl-10' : 'px-4'}
                        ${iconRight ? 'pr-12' : ''}
                        ${className}
                    `}
                    {...props}
                />
                
                {iconRight && (
                    <div className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-400">
                        {iconRight}
                    </div>
                )}
            </div>

            {hasError && (
                <p className="mt-1.5 text-xs text-red-500 font-medium">
                    {errorMessage}
                </p>
            )}
        </div>
    );
});

Input.displayName = 'Input';

export default Input;
