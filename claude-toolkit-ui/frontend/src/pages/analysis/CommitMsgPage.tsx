import { FaCodeBranch } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function CommitMsgPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '커밋 메시지 생성',
        icon: FaCodeBranch,
        iconColor: '#06b6d4',
        description: '코드 변경사항(diff)으로 커밋 메시지를 자동 생성합니다.',
        feature: 'commit_msg',
        inputLabel: 'Diff 입력',
        inputPlaceholder: 'git diff 결과를 입력하세요...',
        options: [
          {
            name: 'style',
            label: '스타일',
            type: 'select',
            defaultValue: 'conventional',
            options: [
              { value: 'conventional', label: 'Conventional' },
              { value: 'gitmoji', label: 'Gitmoji' },
              { value: 'simple', label: '간단' },
              { value: 'detailed', label: '상세' },
            ],
          },
        ],
      }}
    />
  )
}
