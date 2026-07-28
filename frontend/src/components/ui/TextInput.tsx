import { forwardRef } from 'react';
import type { InputHTMLAttributes } from 'react';
import { classNames } from '@/utils/classNames';
import { fieldClass } from './fieldStyles';

/**
 * 한 줄 입력 — 여러 줄이 필요하면 TextArea 를 쓴다.
 *
 * forwardRef로 감싼 이유: react-hook-form의 register()가 반환하는 ref를
 * 실제 <input> DOM 노드까지 전달해야 값을 읽을 수 있다. 없으면 ref가
 * TextInput 선에서 버려져 폼 제출 시 값이 전부 undefined로 온다.
 */
const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...rest }, ref) => (
    <input ref={ref} className={classNames(fieldClass, className)} {...rest} />
  ),
);

TextInput.displayName = 'TextInput';

export default TextInput;
