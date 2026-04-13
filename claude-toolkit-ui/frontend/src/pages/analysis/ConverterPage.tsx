import { FaExchangeAlt } from 'react-icons/fa'
import AnalysisPageTemplate from '../../components/common/AnalysisPageTemplate'

export default function ConverterPage() {
  return (
    <AnalysisPageTemplate
      config={{
        title: '코드 변환',
        icon: FaExchangeAlt,
        iconColor: '#10b981',
        description: 'iBatis → MyBatis, Java 버전 변환 등 코드 마이그레이션을 수행합니다.',
        feature: 'converter',
        inputLabel: '원본 코드',
        sourceMode: 'both',
        allowFileUpload: true,
        inputPlaceholder: 'iBatis XML 또는 변환할 코드를 입력하세요...',
        options: [
          {
            name: 'convertType',
            label: '변환 유형',
            type: 'select',
            defaultValue: 'ibatis_to_mybatis',
            options: [
              { value: 'ibatis_to_mybatis', label: 'iBatis → MyBatis' },
              { value: 'java_upgrade', label: 'Java 버전 업그레이드' },
            ],
          },
        ],
      }}
    />
  )
}
