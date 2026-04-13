import { IconType } from 'react-icons'
import {
  FaHome, FaSearch, FaComments,
  FaLayerGroup, FaProjectDiagram, FaDatabase, FaLanguage, FaChartBar,
  FaSitemap, FaColumns, FaChartLine, FaCodeBranch,
  FaFileAlt, FaCode, FaExchangeAlt, FaTable, FaRandom, FaCubes, FaRocket,
  FaHistory, FaStar, FaCalendarCheck, FaUserCheck,
  FaPlug,
  FaBug, FaEyeSlash, FaUserShield,
  FaUsersCog, FaUserLock, FaShareAlt, FaShieldAlt, FaHeartbeat,
  FaSlidersH, FaMagic, FaServer, FaCog,
} from 'react-icons/fa'

export interface MenuItem {
  label: string
  path: string
  icon: IconType
  color?: string
  adminOnly?: boolean
  reviewerOnly?: boolean
}

export interface MenuSection {
  key: string
  label: string
  items: MenuItem[]
  adminOnly?: boolean
}

export const quickLinks: MenuItem[] = [
  { label: '홈', path: '/', icon: FaHome },
  { label: '검색', path: '/search', icon: FaSearch },
  { label: 'AI 채팅', path: '/chat', icon: FaComments, color: '#8b5cf6' },
]

export const menuSections: MenuSection[] = [
  {
    key: 'analysis',
    label: '분석',
    items: [
      { label: '통합 워크스페이스', path: '/workspace', icon: FaLayerGroup, color: '#f97316' },
      { label: '분석 파이프라인', path: '/pipelines', icon: FaProjectDiagram, color: '#8b5cf6' },
      { label: 'SQL 리뷰', path: '/advisor', icon: FaDatabase, color: '#3b82f6' },
      { label: 'SQL DB 번역', path: '/sql-translate', icon: FaLanguage, color: '#3b82f6' },
      { label: '배치 SQL 분석', path: '/sql-batch', icon: FaLayerGroup, color: '#3b82f6' },
      { label: 'ERD 분석', path: '/erd', icon: FaProjectDiagram, color: '#3b82f6' },
      { label: '복잡도 분석', path: '/complexity', icon: FaChartBar, color: '#3b82f6' },
      { label: '실행계획 분석', path: '/explain', icon: FaSitemap, color: '#3b82f6' },
      { label: '실행계획 비교', path: '/explain/compare', icon: FaColumns, color: '#3b82f6' },
      { label: '성능 히스토리', path: '/explain/dashboard', icon: FaChartLine, color: '#3b82f6' },
      { label: '코드 리뷰 하네스', path: '/harness', icon: FaCodeBranch, color: '#8b5cf6' },
      { label: '배치 분석', path: '/harness/batch', icon: FaLayerGroup, color: '#8b5cf6' },
      { label: '의존성 분석', path: '/harness/dependency', icon: FaProjectDiagram, color: '#8b5cf6' },
      { label: '품질 대시보드', path: '/harness/dashboard', icon: FaChartLine, color: '#8b5cf6' },
    ],
  },
  {
    key: 'generate',
    label: '생성',
    items: [
      { label: '기술 문서', path: '/docgen', icon: FaFileAlt, color: '#10b981' },
      { label: 'API 명세', path: '/apispec', icon: FaCode, color: '#10b981' },
      { label: '코드 변환', path: '/converter', icon: FaExchangeAlt, color: '#10b981' },
      { label: 'Mock 데이터', path: '/mockdata', icon: FaTable, color: '#10b981' },
      { label: 'DB 마이그레이션', path: '/migration', icon: FaRandom, color: '#10b981' },
      { label: 'Batch 처리', path: '/batch', icon: FaLayerGroup, color: '#10b981' },
      { label: '의존성 분석', path: '/depcheck', icon: FaCubes, color: '#10b981' },
      { label: 'Spring 마이그레이션', path: '/migrate', icon: FaRocket, color: '#10b981' },
    ],
  },
  {
    key: 'history',
    label: '기록',
    items: [
      { label: '리뷰 이력', path: '/history', icon: FaHistory, color: '#f59e0b' },
      { label: '즐겨찾기', path: '/favorites', icon: FaStar, color: '#f59e0b' },
      { label: '사용량 모니터링', path: '/usage', icon: FaChartBar, color: '#f59e0b' },
      { label: 'ROI 리포트', path: '/roi-report', icon: FaChartLine, color: '#f59e0b' },
      { label: '분석 스케줄링', path: '/schedule', icon: FaCalendarCheck, color: '#f59e0b' },
      { label: '팀 리뷰 요청', path: '/review-requests', icon: FaUserCheck, color: '#8b5cf6' },
    ],
  },
  {
    key: 'api',
    label: 'REST API',
    items: [
      { label: 'API Playground', path: '/api-docs', icon: FaPlug, color: '#3b82f6' },
    ],
  },
  {
    key: 'tools',
    label: '도구',
    items: [
      { label: '로그 분석기', path: '/loganalyzer', icon: FaBug, color: '#06b6d4' },
      { label: '정규식 생성기', path: '/regex', icon: FaCode, color: '#06b6d4' },
      { label: '커밋 메시지', path: '/commitmsg', icon: FaCodeBranch, color: '#06b6d4' },
      { label: '마스킹 스크립트', path: '/maskgen', icon: FaUserShield, color: '#06b6d4' },
      { label: '민감정보 마스킹', path: '/input-masking', icon: FaEyeSlash, color: '#06b6d4' },
    ],
  },
  {
    key: 'admin',
    label: '관리',
    adminOnly: true,
    items: [
      { label: '사용자 관리', path: '/admin/users', icon: FaUsersCog, color: '#ef4444' },
      { label: '사용자 권한 관리', path: '/admin/permissions', icon: FaUserLock, color: '#ef4444' },
      { label: '팀 설정 공유', path: '/settings/shared', icon: FaShareAlt, color: '#ef4444' },
      { label: '팀 대시보드', path: '/admin/team-dashboard', icon: FaChartLine, color: '#3b82f6' },
      { label: '감사 로그', path: '/admin/audit-dashboard', icon: FaShieldAlt, color: '#f59e0b' },
      { label: '시스템 헬스', path: '/admin/health', icon: FaHeartbeat, color: '#ef4444' },
      { label: 'DB 마이그레이션 가이드', path: '/admin/db-migration', icon: FaExchangeAlt, color: '#3b82f6' },
    ],
  },
]

export const footerItems: MenuItem[] = [
  { label: '프롬프트 템플릿', path: '/prompts', icon: FaSlidersH, color: '#f97316' },
  { label: 'AI 프롬프트 관리', path: '/settings/prompts', icon: FaMagic, color: '#f97316', reviewerOnly: true },
  { label: 'DB 프로필', path: '/db-profiles', icon: FaServer, color: '#3b82f6', adminOnly: true },
  { label: '설정', path: '/settings', icon: FaCog, color: '#64748b', adminOnly: true },
]
