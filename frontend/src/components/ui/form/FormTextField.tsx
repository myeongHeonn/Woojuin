import type { InputHTMLAttributes } from 'react';
import type { FieldValues, Path, UseFormReturn } from 'react-hook-form';
import TextInput from '../TextInput';

interface FormTextFieldProps<T extends FieldValues> extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'name'
> {
  /** react-hook-form에 등록할 필드명 — T에 실제로 있는 키만 허용된다 */
  name: Path<T>;
  /**
   * useForm()의 반환값 전체. register만 뽑아 써도 이 컴포넌트가 RHF에 종속되는
   * 정도는 똑같고, 대신 나중에 watch/setValue가 필요한 변형이 생겨도 이
   * 컴포넌트의 시그니처를 안 바꿔도 된다.
   *
   * 객체 하나로(중첩) 받는 이유: {...formMethods}로 평평하게 펼치면
   * handleSubmit/watch 같은 나머지 메서드까지 ...rest에 섞여 <input> DOM에
   * 그대로 스프레드돼버린다(타입만 좁혀선 못 막는 런타임 문제). 중첩 prop이면
   * 이 컴포넌트가 실제로 쓰는 register/formState만 안전하게 꺼내 쓴다.
   */
  formMethods: UseFormReturn<T>;
}

/** react-hook-form 등록 + 에러 메시지 표시를 한데 묶은 입력 필드. */
function FormTextField<T extends FieldValues>({
  name,
  formMethods,
  ...rest
}: FormTextFieldProps<T>) {
  const {
    register,
    formState: { errors },
  } = formMethods;
  const error = errors[name];

  return (
    <div className="flex flex-col gap-1">
      <TextInput {...register(name)} {...rest} />
      {error && <p className="text-sm text-red-400">{error.message as string}</p>}
    </div>
  );
}

export default FormTextField;
