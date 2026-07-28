import type { InputHTMLAttributes } from 'react';
import type { FieldError, FieldValues, Path, UseFormRegister } from 'react-hook-form';
import TextInput from './TextInput';

interface FormTextFieldProps<T extends FieldValues> extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'name'
> {
  /** react-hook-form에 등록할 필드명 — T에 실제로 있는 키만 허용된다 */
  name: Path<T>;
  register: UseFormRegister<T>;
  error?: FieldError;
}

/**
 * react-hook-form 등록 + 에러 메시지 표시를 한데 묶은 입력 필드.
 *
 * 제네릭인 이유: name이 어떤 폼(T)의 실제 필드인지 컴파일 타임에 검증하기 위해서다
 * (오타로 없는 필드명을 넘기면 타입 에러). TextInput 자체는 폼을 몰라도 되므로
 * 그대로 감싸기만 한다.
 */
function FormTextField<T extends FieldValues>({
  name,
  register,
  error,
  ...rest
}: FormTextFieldProps<T>) {
  return (
    <div className="flex flex-col gap-1">
      <TextInput {...register(name)} {...rest} />
      {error && <p className="text-sm text-red-400">{error.message}</p>}
    </div>
  );
}

export default FormTextField;
