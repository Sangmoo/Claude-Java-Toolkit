import { FaCode } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

const EXAMPLES = [
  { label: '핸드폰번호',   value: '한국 핸드폰 번호를 매칭하는 정규식을 만들어주세요. 010-1234-5678, 01012345678 형식 모두 지원' },
  { label: '주민등록번호', value: '한국 주민등록번호를 매칭하는 정규식. 000000-0000000 형식' },
  { label: '이메일',       value: '이메일 주소를 매칭하는 정규식. user@domain.com 형식' },
  { label: '주소',         value: '한국 주소에서 시/도, 구/군, 동/읍/면을 추출하는 정규식' },
  { label: '이름(한글)',   value: '한글 이름(2~5자)을 매칭하는 정규식' },
  { label: 'IP 주소',      value: 'IPv4 주소를 매칭하는 정규식' },
  { label: 'URL',          value: 'http/https URL을 매칭하는 정규식' },
  { label: '날짜',         value: 'YYYY-MM-DD, YYYY/MM/DD 형식의 날짜를 매칭하는 정규식' },
]

export default function RegexPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '정규식 생성기',
        icon: FaCode,
        iconColor: '#06b6d4',
        description: '요구사항을 설명하면 정규식을 자동 생성합니다. 아래 예시를 클릭하면 입력칸에 자동 입력됩니다.',
        feature: 'regex_gen',
        inputLabel: '요구사항',
        inputPlaceholder: '정규식으로 매칭하고 싶은 패턴을 설명하세요...',
        inputExamples: EXAMPLES,
      }}
    />
  )
}
