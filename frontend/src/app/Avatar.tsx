// 프로필 사진이 없으면 이름 첫 글자로 동그라미를 그린다
export default function Avatar({ name, imageUrl, size = 28 }: { name: string; imageUrl: string | null; size?: number }) {
  const style = { width: size, height: size };
  if (imageUrl) {
    return (
      // 카카오 CDN 사진이라 next/image 의 도메인 허용 목록을 두지 않고 img 로 둔다
      // eslint-disable-next-line @next/next/no-img-element
      <img src={imageUrl} alt="" referrerPolicy="no-referrer" style={style} className="shrink-0 rounded-full object-cover" />
    );
  }
  return (
    <span style={style} aria-hidden className="flex shrink-0 items-center justify-center rounded-full bg-line text-xs">
      {name.slice(0, 1)}
    </span>
  );
}
