import { useEffect, useState, useCallback, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  FaHistory, FaSearch, FaTrash, FaStar, FaCopy, FaCheck,
  FaChevronDown, FaChevronUp, FaDownload, FaCheckCircle, FaTimesCircle, FaClock,
  FaReply, FaComment, FaPaperPlane, FaFileExport, FaShareAlt, FaFileCode, FaInfoCircle, FaFileExcel,
  FaTag, FaTags, FaPlus, FaTimes,
} from 'react-icons/fa'
import { useApi } from '../hooks/useApi'
import { useToast } from '../hooks/useToast'
import { useAuthStore } from '../stores/authStore'
import { copyToClipboard } from '../utils/clipboard'
import { formatDate, formatRelative } from '../utils/date'
import MentionInput, { type MentionCandidate } from '../components/common/MentionInput'
import ReviewActionDialog, { ReviewNoteCard } from '../components/common/ReviewActionDialog'
import { markdownCodeComponents } from '../components/common/CopyableCodeBlock'

interface HistoryItem {
  id: number
  type: string
  title?: string
  inputContent: string
  outputContent: string
  createdAt: string
  username?: string
  reviewStatus?: string
  reviewedBy?: string
  reviewedAt?: string
  reviewNote?: string
  /** v4.7.x — 콤마 구분 태그 문자열 (백엔드에서 전달) */
  tags?: string | null
  /** v4.7.x — JPA `getTagList()` 직렬화 결과. 백엔드 serializer 가 자동 생성 */
  tagList?: string[]
}

interface TagAggregate {
  tag: string
  count: number
}

interface FavoriteRef {
  id: number
  historyId?: number | null
}

interface Comment {
  id: number
  parentId: number | null
  username: string
  content: string
  createdAt: string
  createdAtIso?: string | null  // v4.2.7: formatRelative 용 원본 ISO
}

export default function HistoryPage() {
  const [items, setItems] = useState<HistoryItem[]>([])
  const [filter, setFilter] = useState('')
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const [commentsByHistory, setCommentsByHistory] = useState<Record<number, Comment[]>>({})
  const [newComment, setNewComment] = useState<Record<number, string>>({})
  const [replyParent, setReplyParent] = useState<Record<number, number | null>>({})
  const [replyText, setReplyText] = useState<Record<number, string>>({})
  // v4.2.7: 즐겨찾기된 이력의 historyId → favoriteId 맵. Map 으로 보관해서
  // 별 아이콘 재클릭시 Favorite 레코드를 바로 삭제할 수 있도록 한다 (토글 해제).
  const [favoritedMap, setFavoritedMap] = useState<Map<number, number>>(new Map())
  // v4.2.7: toggleFavorite 연속 클릭 경합 가드 — 진행 중인 historyId 는 재호출 무시
  const togglingIdsRef = useRef<Set<number>>(new Set())
  // v4.2.7: 다중 선택 — 선택된 이력 ID 집합 (내보내기 필터링용)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  // v4.2.7: @멘션 자동완성 후보 사용자 목록 (로그인시 1회 로드)
  const [mentionCandidates, setMentionCandidates] = useState<MentionCandidate[]>([])
  // v4.2.7: 승인/거절 확정 다이얼로그
  const [reviewDialog, setReviewDialog] = useState<{ item: HistoryItem; action: 'ACCEPTED' | 'REJECTED' } | null>(null)
  // v4.2.7: 페이지네이션 — 이력이 page size 를 넘으면 "더 보기" 버튼으로 추가 로드
  const PAGE_SIZE = 50
  const [currentPage, setCurrentPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  // v4.7.x: 태그 필터 (선택된 태그) + 전체 태그 목록 (자동완성/dropdown)
  const [activeTag, setActiveTag] = useState<string>('')
  const [allTags, setAllTags] = useState<TagAggregate[]>([])
  // 태그 편집 중인 history id + 입력 중인 태그 텍스트
  const [tagEditId, setTagEditId] = useState<number | null>(null)
  const [tagEditText, setTagEditText] = useState<string>('')
  const [tagSuggestOpen, setTagSuggestOpen] = useState(false)
  const api = useApi()
  const toast = useToast()
  const user = useAuthStore((s) => s.user)
  const canReview = user?.role === 'ADMIN' || user?.role === 'REVIEWER'
  // v4.2.7: VIEWER 는 이력 삭제 불가 — 삭제 아이콘도 렌더 자체를 제외.
  // user 가 아직 로드되지 않았을 때는 false(숨김) 로 시작해야 일시적으로 노출되는 것을 방지한다.
  // ADMIN / REVIEWER 만 명시적으로 true — 백엔드(ReviewHistoryController.delete) 에서도 동일 정책을 강제한다.
  const canDelete = user?.role === 'ADMIN' || user?.role === 'REVIEWER'

  // v4.2.7: 페이지네이션 지원 — 응답 헤더 X-Has-More 로 "더 보기" 버튼 제어
  // v4.7.x: tag 필터가 설정되어 있으면 ?tag= 파라미터 동봉 (서버에서 정확 매칭)
  const loadHistory = useCallback(async () => {
    try {
      const tagParam = activeTag ? `&tag=${encodeURIComponent(activeTag)}` : ''
      const res = await fetch(`/api/v1/history?page=0&size=${PAGE_SIZE}${tagParam}`, { credentials: 'include' })
      if (!res.ok) return
      const json = await res.json()
      const data = (json.data ?? json) as HistoryItem[]
      setItems(Array.isArray(data) ? data : [])
      setCurrentPage(0)
      setHasMore((res.headers.get('X-Has-More') || 'false') === 'true')
    } catch { /* silent */ }
  }, [activeTag])

  const loadMoreHistory = async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const nextPage = currentPage + 1
      const tagParam = activeTag ? `&tag=${encodeURIComponent(activeTag)}` : ''
      const res = await fetch(`/api/v1/history?page=${nextPage}&size=${PAGE_SIZE}${tagParam}`, { credentials: 'include' })
      if (!res.ok) return
      const json = await res.json()
      const data = (json.data ?? json) as HistoryItem[]
      if (Array.isArray(data) && data.length > 0) {
        setItems((prev) => [...prev, ...data])
        setCurrentPage(nextPage)
      }
      setHasMore((res.headers.get('X-Has-More') || 'false') === 'true')
    } catch { /* silent */ }
    finally { setLoadingMore(false) }
  }

  // v4.7.x: 사용자의 모든 태그를 빈도순으로 로드 (자동완성/dropdown 용)
  const loadAllTags = useCallback(async () => {
    try {
      const res = await fetch('/history/tags/all', { credentials: 'include' })
      if (!res.ok) return
      const d = await res.json()
      if (d?.success && Array.isArray(d.tags)) setAllTags(d.tags as TagAggregate[])
    } catch { /* silent */ }
  }, [])

  // v4.7.x: 태그 업데이트 — 콤마 구분 문자열 전송, 응답으로 정규화된 결과 반영
  const saveTags = async (id: number, rawTags: string) => {
    try {
      const res = await fetch(`/history/${id}/tags`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ tags: rawTags }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (res.ok && d?.success) {
        const tagsStr  = (d.tags as string | undefined) || ''
        const tagList  = (d.tagList as string[] | undefined) || []
        setItems((prev) => prev.map((i) => i.id === id
          ? { ...i, tags: tagsStr, tagList }
          : i))
        toast.success('태그가 저장되었습니다.')
        loadAllTags()  // 빈도 집계 갱신
      } else {
        toast.error(d?.error || '태그 저장 실패')
      }
    } catch {
      toast.error('태그 저장 요청 실패')
    } finally {
      setTagEditId(null)
      setTagEditText('')
      setTagSuggestOpen(false)
    }
  }

  // 태그 클릭 → 활성 필터 토글 (이미 활성이면 해제)
  const toggleTagFilter = (tag: string) => {
    setActiveTag((prev) => prev === tag ? '' : tag)
  }

  // v4.2.7: 페이지 로드시 즐겨찾기 목록을 받아서 historyId → favoriteId 맵 구성
  const loadFavorited = useCallback(async () => {
    const favs = await api.get('/api/v1/favorites') as FavoriteRef[] | null
    if (favs) {
      const m = new Map<number, number>()
      favs.forEach((f) => { if (f.historyId != null) m.set(f.historyId, f.id) })
      setFavoritedMap(m)
    }
  }, [])

  // v4.2.7: @멘션 자동완성 후보 로드
  const loadMentionCandidates = useCallback(async () => {
    const list = await api.get('/api/v1/users/mentions') as MentionCandidate[] | null
    if (list) setMentionCandidates(list)
  }, [])

  useEffect(() => { loadHistory(); loadFavorited(); loadMentionCandidates(); loadAllTags() }, [loadHistory, loadFavorited, loadMentionCandidates, loadAllTags])

  const loadComments = async (historyId: number) => {
    try {
      const res = await fetch(`/history/${historyId}/comments`, { credentials: 'include' })
      if (res.ok) {
        const list: Comment[] = await res.json()
        setCommentsByHistory((prev) => ({ ...prev, [historyId]: list }))
      }
    } catch { /* ignore */ }
  }

  const toggleExpand = (id: number) => {
    if (expandedId === id) {
      setExpandedId(null)
    } else {
      setExpandedId(id)
      loadComments(id)
    }
  }

  const filtered = items.filter((item) => {
    if (!filter) return true
    const q = filter.toLowerCase()
    return (
      item.type?.toLowerCase().includes(q) ||
      item.inputContent?.toLowerCase().includes(q) ||
      item.title?.toLowerCase().includes(q)
    )
  })

  const deleteItem = async (id: number) => {
    // v4.2.7: 방어적 프론트 체크 — 실제 권한 강제는 백엔드가 담당
    if (!canDelete) {
      toast.error('삭제 권한이 없습니다.')
      return
    }
    if (!confirm('삭제하시겠습니까?')) return
    try {
      const res = await fetch(`/history/${id}/delete`, { method: 'POST', credentials: 'include' })
      const d  = await res.json().catch(() => null)
      if (res.ok && d?.success) {
        setItems((prev) => prev.filter((i) => i.id !== id))
        toast.success('삭제되었습니다.')
      } else {
        toast.error(d?.error || '삭제 실패')
      }
    } catch {
      toast.error('삭제 요청 실패')
    }
  }

  // v4.2.8: 공유 링크 생성 — 7일간 유효한 read-only 링크 + 단축 URL
  // 생성 즉시 링크를 클립보드에 복사.
  const shareHistory = async (item: HistoryItem) => {
    try {
      const res = await fetch(`/history/${item.id}/share`, {
        method: 'POST',
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (res.ok && d?.success && d?.shareUrl) {
        const fullUrl = `${window.location.origin}${d.shareUrl}`
        const ok = await copyToClipboard(fullUrl)
        if (ok) {
          toast.success(`공유 링크 복사됨 (${d.remaining})`)
        } else {
          toast.info(`공유 링크: ${fullUrl}`)
        }
      } else {
        toast.error(d?.error || '공유 링크 생성 실패')
      }
    } catch {
      toast.error('요청 실패')
    }
  }

  // v4.3.0: SARIF 2.1.0 JSON 다운로드 — VS Code SARIF Viewer / JetBrains Qodana / GitHub Code Scanning 호환
  const downloadSarif = async (item: HistoryItem) => {
    try {
      const res = await fetch(`/api/v1/export/sarif/${item.id}`, {
        credentials: 'include',
      })
      if (!res.ok) {
        toast.error(`SARIF 내보내기 실패 (HTTP ${res.status})`)
        return
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `claude-toolkit-${item.type}-${item.id}.sarif`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('SARIF 파일이 다운로드되었습니다')
    } catch {
      toast.error('SARIF 다운로드 요청 실패')
    }
  }

  // v4.3.0: SARIF 도움말 토글 (어떤 IDE에서 어떻게 보는지 안내)
  const [sarifHelpId, setSarifHelpId] = useState<number | null>(null)

  // v4.3.0: Excel(.xlsx) 일괄 다운로드 — 최근 1000건 (3개 시트: 요약/이력 상세/유형별 통계)
  const downloadExcel = async () => {
    try {
      const res = await fetch('/api/v1/export/excel/history?limit=1000', {
        credentials: 'include',
      })
      if (!res.ok) {
        toast.error(`Excel 내보내기 실패 (HTTP ${res.status})`)
        return
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      const ts = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
      a.download = `claude-toolkit-history-${ts}.xlsx`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('Excel 파일이 다운로드되었습니다 (최근 1000건)')
    } catch {
      toast.error('Excel 다운로드 요청 실패')
    }
  }

  // v4.2.7: 다중 선택 삭제 — 선택된 이력을 일괄로 삭제. 개별 엔드포인트를
  // 병렬 호출하여 백엔드 역할 체크(VIEWER 차단)를 그대로 활용한다.
  const deleteSelected = async () => {
    if (!canDelete) {
      toast.error('삭제 권한이 없습니다.')
      return
    }
    if (selectedIds.size === 0) return
    if (!confirm(`선택한 ${selectedIds.size}건을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`)) return
    const ids = Array.from(selectedIds)
    const results = await Promise.all(ids.map(async (id) => {
      try {
        const res = await fetch(`/history/${id}/delete`, { method: 'POST', credentials: 'include' })
        const d   = await res.json().catch(() => null)
        return { id, ok: !!(res.ok && d?.success), error: d?.error as string | undefined }
      } catch {
        return { id, ok: false, error: '요청 실패' }
      }
    }))
    const okIds = new Set(results.filter((r) => r.ok).map((r) => r.id))
    const failCount = results.length - okIds.size
    setItems((prev) => prev.filter((i) => !okIds.has(i.id)))
    setSelectedIds((prev) => {
      const next = new Set(prev)
      okIds.forEach((id) => next.delete(id))
      return next
    })
    if (okIds.size > 0) toast.success(`${okIds.size}건 삭제되었습니다.`)
    if (failCount > 0) toast.error(`${failCount}건 삭제 실패`)
  }

  // v4.2.7: 별 아이콘 클릭 → 토글 동작
  //   - 즐겨찾기 상태가 아니면 → /favorites/star 로 추가 + 노란 별
  //   - 이미 즐겨찾기 상태면 → /favorites/{favoriteId}/delete 로 해제 + 기본 별
  //
  // 경합 가드: 동일 historyId 에 대해 이미 요청 진행 중이면 중복 호출을 무시한다.
  // 빠른 더블클릭으로 star → delete → star 가 엉켜 상태가 꼬이는 걸 방지.
  const toggleFavorite = async (item: HistoryItem) => {
    if (togglingIdsRef.current.has(item.id)) return
    togglingIdsRef.current.add(item.id)
    try {
      await doToggleFavorite(item)
    } finally {
      togglingIdsRef.current.delete(item.id)
    }
  }

  const doToggleFavorite = async (item: HistoryItem) => {
    const existingFavId = favoritedMap.get(item.id)

    // ── 해제 ─────────────────────────────────────────────────────
    if (existingFavId != null) {
      try {
        const res = await fetch(`/favorites/${existingFavId}/delete`, {
          method: 'POST',
          credentials: 'include',
        })
        const d = await res.json().catch(() => null)
        if (res.ok && d?.success) {
          setFavoritedMap((prev) => {
            const next = new Map(prev)
            next.delete(item.id)
            return next
          })
          toast.success('즐겨찾기에서 해제되었습니다.')
        } else {
          toast.error(d?.error || '즐겨찾기 해제 실패')
        }
      } catch {
        toast.error('요청 실패')
      }
      return
    }

    // ── 추가 ─────────────────────────────────────────────────────
    try {
      const res = await fetch('/favorites/star', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ historyId: String(item.id) }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d && d.success) {
        const newFavId = typeof d.favoriteId === 'number' ? d.favoriteId : -1
        setFavoritedMap((prev) => {
          const next = new Map(prev)
          next.set(item.id, newFavId)
          return next
        })
        toast.success('즐겨찾기에 추가되었습니다.')
      } else {
        toast.error((d && d.error) || '즐겨찾기 추가 실패')
      }
    } catch {
      toast.error('요청 실패')
    }
  }

  // v4.2.7: 다중 선택 토글
  const toggleSelect = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }
  const toggleSelectAll = () => {
    if (selectedIds.size === filtered.length && filtered.length > 0) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(filtered.map((i) => i.id)))
    }
  }

  const copyResult = async (item: HistoryItem) => {
    const ok = await copyToClipboard(item.outputContent || '')
    if (ok) {
      setCopiedId(item.id)
      setTimeout(() => setCopiedId(null), 3000)
      toast.success('복사되었습니다.')
    } else {
      toast.error('복사 실패')
    }
  }

  // v4.2.7: 선택된 항목이 있으면 선택분만, 없으면 필터 결과 전체를 내보냄
  const exportAll = () => {
    const target = selectedIds.size > 0
      ? filtered.filter((i) => selectedIds.has(i.id))
      : filtered
    if (target.length === 0) {
      toast.error('내보낼 이력이 없습니다.')
      return
    }
    const md = target.map((i) =>
      `## ${i.type} — ${i.createdAt}\n\n### 입력\n\`\`\`\n${i.inputContent}\n\`\`\`\n\n### 결과\n${i.outputContent}\n\n---\n`
    ).join('\n')
    const blob = new Blob([md], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const suffix = selectedIds.size > 0 ? `selected_${target.length}` : 'all'
    a.download = `history_${suffix}_${new Date().toISOString().slice(0, 10)}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  // v4.2.7: 승인/거절 버튼 → 다이얼로그 오픈 (코멘트 입력 가능)
  const openReviewDialog = (item: HistoryItem, action: 'ACCEPTED' | 'REJECTED') => {
    setReviewDialog({ item, action })
  }
  // 다이얼로그 확정시 호출 — note 는 선택 입력
  const submitReview = async (item: HistoryItem, status: 'ACCEPTED' | 'REJECTED', note: string) => {
    const action = status === 'ACCEPTED' ? '승인' : '거절'
    try {
      const res = await fetch(`/history/${item.id}/review-status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ status, note }),
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        toast.success(`${action}되었습니다.`)
        setItems((prev) => prev.map((i) => i.id === item.id
          ? { ...i, reviewStatus: status, reviewedBy: user?.username, reviewedAt: new Date().toISOString(), reviewNote: note }
          : i))
      } else {
        toast.error(d?.error || `${action} 실패`)
      }
    } catch {
      toast.error(`${action} 요청 실패`)
    } finally {
      setReviewDialog(null)
    }
  }

  const postComment = async (historyId: number, content: string, parentId: number | null = null) => {
    if (!content.trim()) { toast.error('댓글 내용을 입력하세요.'); return }
    try {
      const body = new URLSearchParams({ content: content.trim() })
      if (parentId != null) body.set('parentId', String(parentId))
      const res = await fetch(`/history/${historyId}/comments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
        credentials: 'include',
      })
      const d = await res.json().catch(() => null)
      if (d?.success) {
        if (parentId != null) {
          setReplyText((prev) => ({ ...prev, [historyId]: '' }))
          setReplyParent((prev) => ({ ...prev, [historyId]: null }))
        } else {
          setNewComment((prev) => ({ ...prev, [historyId]: '' }))
        }
        loadComments(historyId)
      } else {
        toast.error(d?.error || '작성 실패')
      }
    } catch { toast.error('요청 실패') }
  }

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FaHistory style={{ color: '#f59e0b' }} /> 리뷰 이력
          <span style={{ fontSize: '13px', color: 'var(--text-muted)', fontWeight: 400 }}>({filtered.length}건)</span>
          {selectedIds.size > 0 && (
            <span style={{ fontSize: '12px', color: 'var(--accent)', fontWeight: 600 }}>
              · {selectedIds.size}건 선택됨
            </span>
          )}
          {/* v4.7.x: 활성 태그 필터 — 클릭하면 해제 */}
          {activeTag && (
            <span
              onClick={() => setActiveTag('')}
              title="태그 필터 해제"
              style={{
                fontSize: '12px', fontWeight: 600,
                padding: '3px 10px', borderRadius: '12px',
                background: '#3b82f6', color: '#fff', cursor: 'pointer',
                display: 'inline-flex', alignItems: 'center', gap: '4px',
              }}>
              <FaTag style={{ fontSize: '10px' }} /> {activeTag} <FaTimes style={{ fontSize: '10px' }} />
            </span>
          )}
        </h2>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ position: 'relative' }}>
            <FaSearch style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)', fontSize: '13px' }} />
            <input
              style={{ paddingLeft: '30px', width: '220px', fontSize: '13px' }}
              placeholder="검색..."
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          {/* v4.7.x: 태그 dropdown — 빈도순 상위 태그 빠른 선택 */}
          {allTags.length > 0 && (
            <select
              value={activeTag}
              onChange={(e) => setActiveTag(e.target.value)}
              style={{
                fontSize: '13px', padding: '6px 10px',
                border: '1px solid var(--border-color)', borderRadius: '6px',
                background: activeTag ? '#3b82f6' : 'transparent',
                color: activeTag ? '#fff' : 'var(--text-sub)',
                cursor: 'pointer',
              }}
              title="태그로 필터링">
              <option value="">전체 태그</option>
              {allTags.map((t) => (
                <option key={t.tag} value={t.tag}>#{t.tag} ({t.count})</option>
              ))}
            </select>
          )}
          {/* v4.2.7: 전체선택 토글 */}
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: 'var(--text-sub)', cursor: filtered.length ? 'pointer' : 'not-allowed', userSelect: 'none' }}>
            <input
              type="checkbox"
              checked={filtered.length > 0 && selectedIds.size === filtered.length}
              ref={(el) => { if (el) el.indeterminate = selectedIds.size > 0 && selectedIds.size < filtered.length }}
              onChange={toggleSelectAll}
              disabled={filtered.length === 0}
            />
            전체선택
          </label>
          {selectedIds.size > 0 && (
            <button onClick={() => setSelectedIds(new Set())} style={{ ...btnStyle, color: 'var(--text-muted)' }} title="선택 해제">
              해제
            </button>
          )}
          {/* v4.2.7: 선택 삭제 — canDelete(=ADMIN/REVIEWER)일 때만 노출 */}
          {canDelete && selectedIds.size > 0 && (
            <button
              onClick={deleteSelected}
              style={{ ...btnStyle, background: 'var(--red)', color: '#fff', borderColor: 'var(--red)' }}
              title={`선택 ${selectedIds.size}건 삭제`}>
              <FaTrash /> 선택 {selectedIds.size}건 삭제
            </button>
          )}
          <button
            onClick={exportAll}
            style={selectedIds.size > 0 ? { ...btnStyle, background: 'var(--accent)', color: '#fff', borderColor: 'var(--accent)' } : btnStyle}
            title={selectedIds.size > 0 ? `선택 ${selectedIds.size}건 Markdown 내보내기` : '전체 Markdown 내보내기'}>
            {selectedIds.size > 0 ? <FaFileExport /> : <FaDownload />}
            {selectedIds.size > 0 ? ` 선택 ${selectedIds.size}건 (MD)` : ' 내보내기 (MD)'}
          </button>
          {/* v4.3.0: Excel 워크북 다운로드 (3 시트: 요약/이력 상세/유형별 통계) */}
          <button
            onClick={downloadExcel}
            style={{ ...btnStyle, background: '#1f7244', color: '#fff', borderColor: '#1f7244' }}
            title="Excel(.xlsx) 다운로드 — 최근 1000건, 시트 분리 + 합계 수식">
            <FaFileExcel /> Excel
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {filtered.map((item) => {
          const comments = commentsByHistory[item.id] || []
          const topComments = comments.filter((c) => c.parentId == null)
          const repliesByParent: Record<number, Comment[]> = {}
          comments.forEach((c) => {
            if (c.parentId != null) {
              if (!repliesByParent[c.parentId]) repliesByParent[c.parentId] = []
              repliesByParent[c.parentId].push(c)
            }
          })

          const isFavorited = favoritedMap.has(item.id)
          const isSelected  = selectedIds.has(item.id)
          return (
            <div key={item.id} style={isSelected ? { ...cardStyle, borderColor: 'var(--accent)', boxShadow: '0 0 0 1px var(--accent)' } : cardStyle}>
              <div
                style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 16px', cursor: 'pointer' }}
                onClick={() => toggleExpand(item.id)}
              >
                {/* v4.2.7: 다중 선택 체크박스 — 행 확장과 분리 */}
                <input
                  type="checkbox"
                  checked={isSelected}
                  onChange={() => toggleSelect(item.id)}
                  onClick={(e) => e.stopPropagation()}
                  style={{ flexShrink: 0, cursor: 'pointer' }}
                  title="내보내기 선택"
                />
                <span style={badgeStyle}>{item.type}</span>
                <ReviewStatusBadge status={item.reviewStatus} />
                {/* v4.7.x: 태그 chip — 클릭하면 해당 태그로 필터 토글 */}
                {(item.tagList && item.tagList.length > 0) && (
                  <div style={{ display: 'flex', gap: '3px', flexShrink: 0 }}>
                    {item.tagList.slice(0, 3).map((t) => (
                      <span
                        key={t}
                        onClick={(e) => { e.stopPropagation(); toggleTagFilter(t) }}
                        title={`태그 #${t} 로 필터`}
                        style={{
                          fontSize: '10px', padding: '1px 7px', borderRadius: '10px',
                          background: activeTag === t ? '#3b82f6' : 'rgba(59,130,246,0.12)',
                          color:      activeTag === t ? '#fff'    : '#3b82f6',
                          cursor: 'pointer', fontWeight: 600, whiteSpace: 'nowrap',
                        }}>
                        #{t}
                      </span>
                    ))}
                    {item.tagList.length > 3 && (
                      <span style={{ fontSize: '10px', color: 'var(--text-muted)', alignSelf: 'center' }} title={`+${item.tagList.length - 3}개 더`}>
                        +{item.tagList.length - 3}
                      </span>
                    )}
                  </div>
                )}
                <span style={{ flex: 1, fontSize: '13px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-sub)' }}>
                  {item.title || item.inputContent?.slice(0, 80)}
                </span>
                <span
                  style={{ fontSize: '12px', color: 'var(--text-muted)', flexShrink: 0 }}
                  title={formatDate(item.createdAt)}
                >
                  {formatRelative(item.createdAt)}
                </span>
                <div style={{ display: 'flex', gap: '4px' }}>
                  <button style={iconBtnStyle} onClick={(e) => { e.stopPropagation(); copyResult(item) }}>
                    {copiedId === item.id ? <FaCheck style={{ color: 'var(--green)' }} /> : <FaCopy />}
                  </button>
                  {/* v4.2.7: 즐겨찾기 토글 — 반영 상태에 따라 노랑 별, 재클릭시 해제 */}
                  <button
                    style={{ ...iconBtnStyle, color: isFavorited ? '#f59e0b' : 'var(--text-muted)' }}
                    onClick={(e) => { e.stopPropagation(); toggleFavorite(item) }}
                    title={isFavorited ? '즐겨찾기 해제' : '즐겨찾기에 추가'}
                  >
                    <FaStar />
                  </button>
                  {/* v4.2.8: 공유 링크 (7일간 유효, 로그인 없이 접근 가능) */}
                  <button
                    style={{ ...iconBtnStyle, color: 'var(--blue)' }}
                    onClick={(e) => { e.stopPropagation(); shareHistory(item) }}
                    title="공유 링크 복사 (7일 유효)"
                  >
                    <FaShareAlt />
                  </button>
                  {/* v4.3.0: SARIF 2.1.0 JSON 다운로드 — IDE 연동용 */}
                  <button
                    style={{ ...iconBtnStyle, color: '#8b5cf6' }}
                    onClick={(e) => { e.stopPropagation(); downloadSarif(item) }}
                    title="SARIF 다운로드 (IDE 연동)"
                  >
                    <FaFileCode />
                  </button>
                  <button
                    style={{ ...iconBtnStyle, color: 'var(--text-muted)', fontSize: '12px' }}
                    onClick={(e) => { e.stopPropagation(); setSarifHelpId(sarifHelpId === item.id ? null : item.id) }}
                    title="SARIF 사용법 안내"
                  >
                    <FaInfoCircle />
                  </button>
                  {/* v4.2.7: VIEWER 권한은 삭제 아이콘 자체를 렌더하지 않음 */}
                  {canDelete && (
                    <button
                      style={{ ...iconBtnStyle, color: 'var(--red)' }}
                      onClick={(e) => { e.stopPropagation(); deleteItem(item.id) }}
                      title="삭제"
                    >
                      <FaTrash />
                    </button>
                  )}
                </div>
                {expandedId === item.id ? <FaChevronUp style={{ color: 'var(--text-muted)' }} /> : <FaChevronDown style={{ color: 'var(--text-muted)' }} />}
              </div>

              {/* v4.3.0: SARIF 사용 가이드 패널 */}
              {sarifHelpId === item.id && (
                <div
                  style={{
                    borderTop: '1px solid var(--border-color)',
                    background: 'var(--bg-subtle, #f8fafc)',
                    padding: '14px 16px',
                    fontSize: '13px',
                    lineHeight: 1.6,
                    color: 'var(--text-default)',
                  }}
                  onClick={(e) => e.stopPropagation()}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '8px', fontWeight: 600 }}>
                    <FaInfoCircle style={{ color: '#8b5cf6' }} /> SARIF 파일 사용 방법
                  </div>
                  <p style={{ margin: '4px 0 10px', color: 'var(--text-muted)' }}>
                    다운로드한 <code>.sarif</code> 파일은 정적 분석 결과의 표준 포맷(SARIF 2.1.0)입니다.
                    아래 도구에서 인라인 마커와 함께 확인할 수 있습니다.
                  </p>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '10px', marginTop: '8px' }}>
                    <div style={{ background: 'var(--bg-card, #fff)', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '10px 12px' }}>
                      <strong style={{ color: '#0078d4' }}>VS Code</strong>
                      <ol style={{ margin: '6px 0 0 18px', padding: 0 }}>
                        <li>마켓플레이스에서 <code>SARIF Viewer</code> (Microsoft DevLabs) 설치</li>
                        <li>다운로드한 <code>.sarif</code> 파일을 VS Code 로 드래그</li>
                        <li>좌측 SARIF 패널에서 이슈 트리 + 코드 인라인 마커 확인</li>
                      </ol>
                    </div>

                    <div style={{ background: 'var(--bg-card, #fff)', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '10px 12px' }}>
                      <strong style={{ color: '#fe315e' }}>JetBrains IDE (IntelliJ/PyCharm/WebStorm)</strong>
                      <ol style={{ margin: '6px 0 0 18px', padding: 0 }}>
                        <li>플러그인 마켓플레이스에서 <code>Qodana</code> 또는 <code>SARIF Viewer</code> 설치</li>
                        <li>Tools → Qodana → Show SARIF report 메뉴에서 파일 선택</li>
                        <li>Problems 패널에서 결과 확인</li>
                      </ol>
                    </div>

                    <div style={{ background: 'var(--bg-card, #fff)', border: '1px solid var(--border-color)', borderRadius: '6px', padding: '10px 12px' }}>
                      <strong style={{ color: '#24292f' }}>GitHub Code Scanning</strong>
                      <ol style={{ margin: '6px 0 0 18px', padding: 0 }}>
                        <li>저장소 Settings → Security → Code scanning 활성화</li>
                        <li>GitHub Actions 워크플로에서 <code>github/codeql-action/upload-sarif@v3</code> 액션으로 업로드</li>
                        <li>PR 의 Files changed 탭에 자동 코멘트 + Security 탭에서 추적</li>
                      </ol>
                    </div>
                  </div>

                  <div style={{ marginTop: '10px', fontSize: '12px', color: 'var(--text-muted)' }}>
                    💡 입력 코드에 파일명/라인 정보가 포함되어 있지 않으므로 위치는 가상의 <code>analysis-input.txt</code> 로 표시됩니다.
                    실제 IDE 통합을 원하시면 GitHub Actions 에서 분석 결과를 SARIF 로 업로드하는 방식이 가장 효과적입니다.
                  </div>
                </div>
              )}

              {expandedId === item.id && (
                <div style={{ borderTop: '1px solid var(--border-color)', padding: '16px' }}>
                  {/* 리뷰 승인/거절 영역 */}
                  <div style={reviewBarStyle}>
                    <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                      <ReviewStatusBadge status={item.reviewStatus} large />
                      {item.reviewedBy && (
                        <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                          {item.reviewedBy} · {formatDate(item.reviewedAt)}
                        </span>
                      )}
                    </div>
                    {canReview ? (
                      <div style={{ display: 'flex', gap: '6px' }}>
                        <button
                          onClick={() => openReviewDialog(item, 'ACCEPTED')}
                          style={{ ...actionBtnStyle, background: '#10b981', color: '#fff', borderColor: '#10b981' }}>
                          <FaCheckCircle /> 승인
                        </button>
                        <button
                          onClick={() => openReviewDialog(item, 'REJECTED')}
                          style={{ ...actionBtnStyle, background: '#ef4444', color: '#fff', borderColor: '#ef4444' }}>
                          <FaTimesCircle /> 거절
                        </button>
                      </div>
                    ) : (
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        REVIEWER/ADMIN 만 승인·거절 가능
                      </span>
                    )}
                  </div>

                  {/* v4.2.7: 리뷰 코멘트 카드 — 모든 사용자에게 리뷰어의 피드백을 친화적으로 표시 */}
                  <ReviewNoteCard
                    status={item.reviewStatus || 'PENDING'}
                    reviewedBy={item.reviewedBy}
                    reviewedAt={item.reviewedAt ? formatDate(item.reviewedAt) : undefined}
                    note={item.reviewNote}
                  />

                  {/* v4.7.x: 태그 편집 — 본인 소유 이력만 편집 가능 (UI 만 — 백엔드도 동일 정책) */}
                  <TagEditor
                    item={item}
                    isOwner={!!user?.username && (user.username === item.username)}
                    isEditing={tagEditId === item.id}
                    editText={tagEditText}
                    setEditText={setTagEditText}
                    suggestOpen={tagSuggestOpen}
                    setSuggestOpen={setTagSuggestOpen}
                    allTags={allTags}
                    onStartEdit={() => {
                      setTagEditId(item.id)
                      setTagEditText(item.tags || '')
                      setTagSuggestOpen(true)
                    }}
                    onCancel={() => { setTagEditId(null); setTagEditText(''); setTagSuggestOpen(false) }}
                    onSave={() => saveTags(item.id, tagEditText)}
                    onTagClick={toggleTagFilter}
                    activeTag={activeTag}
                  />

                  <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px', marginTop: '14px' }}>입력</h4>
                  <pre style={{ background: 'var(--bg-primary)', padding: '10px', borderRadius: '6px', fontSize: '12px', marginBottom: '12px', whiteSpace: 'pre-wrap', maxHeight: '150px', overflow: 'auto' }}>
                    {item.inputContent}
                  </pre>
                  <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>결과</h4>
                  <div className="markdown-body" style={{ fontSize: '13px' }}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownCodeComponents}>{item.outputContent || ''}</ReactMarkdown>
                  </div>

                  {/* 댓글 섹션 */}
                  <div style={{ marginTop: '20px', borderTop: '1px solid var(--border-color)', paddingTop: '14px' }}>
                    <h4 style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <FaComment /> 댓글 ({comments.length})
                    </h4>
                    {topComments.map((c) => (
                      <div key={c.id} style={{ marginBottom: '10px' }}>
                        <CommentBox comment={c} />
                        <button
                          onClick={() => setReplyParent((prev) => ({ ...prev, [item.id]: prev[item.id] === c.id ? null : c.id }))}
                          style={{ background: 'none', border: 'none', color: 'var(--accent)', fontSize: '11px', cursor: 'pointer', marginLeft: '34px', marginTop: '2px' }}>
                          <FaReply style={{ fontSize: '9px' }} /> 답글
                        </button>
                        {(repliesByParent[c.id] || []).map((r) => (
                          <div key={r.id} style={{ marginLeft: '32px', marginTop: '6px' }}>
                            <CommentBox comment={r} isReply />
                          </div>
                        ))}
                        {replyParent[item.id] === c.id && (
                          <div style={{ marginLeft: '32px', marginTop: '6px', display: 'flex', gap: '6px' }}>
                            <MentionInput
                              value={replyText[item.id] || ''}
                              onChange={(v) => setReplyText((prev) => ({ ...prev, [item.id]: v }))}
                              candidates={mentionCandidates}
                              onEnter={() => postComment(item.id, replyText[item.id] || '', c.id)}
                              placeholder="답글... (@로 사용자 호출)"
                              style={commentInputStyle}
                            />
                            <button
                              onClick={() => postComment(item.id, replyText[item.id] || '', c.id)}
                              style={commentSendStyle}><FaPaperPlane /></button>
                          </div>
                        )}
                      </div>
                    ))}
                    {topComments.length === 0 && (
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)', textAlign: 'center', padding: '12px' }}>
                        첫 댓글을 남겨보세요.
                      </div>
                    )}
                    {/* 새 댓글 입력 */}
                    <div style={{ display: 'flex', gap: '6px', marginTop: '12px' }}>
                      <MentionInput
                        value={newComment[item.id] || ''}
                        onChange={(v) => setNewComment((prev) => ({ ...prev, [item.id]: v }))}
                        candidates={mentionCandidates}
                        onEnter={() => postComment(item.id, newComment[item.id] || '')}
                        placeholder="댓글을 입력하세요... (@로 사용자 호출)"
                        style={commentInputStyle}
                      />
                      <button
                        onClick={() => postComment(item.id, newComment[item.id] || '')}
                        style={commentSendStyle}><FaPaperPlane /> 작성</button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )
        })}
        {filtered.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text-muted)' }}>
            <FaHistory style={{ fontSize: '36px', opacity: 0.3, marginBottom: '12px' }} />
            <p>이력이 없습니다.</p>
          </div>
        )}
        {/* v4.2.7: 더 보기 — 서버에 다음 페이지가 남아 있을 때만 노출 */}
        {hasMore && (
          <div style={{ textAlign: 'center', padding: '12px' }}>
            <button
              onClick={loadMoreHistory}
              disabled={loadingMore}
              style={{
                ...btnStyle,
                padding: '8px 24px',
                opacity: loadingMore ? 0.6 : 1,
                cursor: loadingMore ? 'wait' : 'pointer',
              }}>
              {loadingMore ? '불러오는 중...' : `더 보기 (+${PAGE_SIZE})`}
            </button>
          </div>
        )}
      </div>

      {/* v4.2.7: 승인/거절 확정 다이얼로그 */}
      {reviewDialog && (
        <ReviewActionDialog
          action={reviewDialog.action}
          targetTitle={reviewDialog.item.title || reviewDialog.item.inputContent?.slice(0, 80)}
          onConfirm={(note) => submitReview(reviewDialog.item, reviewDialog.action, note)}
          onCancel={() => setReviewDialog(null)}
        />
      )}
    </>
  )
}

/**
 * v4.7.x — 이력 항목 확장 영역에 들어가는 태그 편집기.
 *
 * - 보기 모드: 태그 chip 들 + 편집 버튼 (소유자만 노출)
 * - 편집 모드: 콤마 구분 텍스트 입력 + 자동완성 dropdown (사용자가 가진 다른 태그)
 *   + 저장/취소 버튼
 */
function TagEditor({
  item, isOwner, isEditing, editText, setEditText,
  suggestOpen, setSuggestOpen, allTags,
  onStartEdit, onCancel, onSave, onTagClick, activeTag,
}: {
  item: HistoryItem
  isOwner: boolean
  isEditing: boolean
  editText: string
  setEditText: (s: string) => void
  suggestOpen: boolean
  setSuggestOpen: (b: boolean) => void
  allTags: TagAggregate[]
  onStartEdit: () => void
  onCancel: () => void
  onSave: () => void
  onTagClick: (t: string) => void
  activeTag: string
}) {
  // 편집 중 입력의 마지막 토큰을 추출해서 자동완성 후보로 사용
  const lastToken = (() => {
    if (!editText) return ''
    const parts = editText.split(',')
    return (parts[parts.length - 1] || '').trim().toLowerCase()
  })()
  const filteredSuggest = allTags
    .filter((t) => !editText.toLowerCase().split(',').map(s => s.trim()).includes(t.tag.toLowerCase()))
    .filter((t) => !lastToken || t.tag.toLowerCase().includes(lastToken))
    .slice(0, 12)

  const appendTag = (tag: string) => {
    const parts = editText.split(',')
    parts[parts.length - 1] = ` ${tag}`
    const next = parts.join(',').replace(/^\s*,/, '').trim() + ', '
    setEditText(next)
  }

  const tags = item.tagList || []

  return (
    <div style={{
      marginTop: '10px',
      padding: '8px 10px',
      background: 'var(--bg-primary)',
      border: '1px solid var(--border-color)',
      borderRadius: '8px',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
        <FaTags style={{ color: '#3b82f6', fontSize: '12px' }} />
        <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>태그</span>

        {!isEditing ? (
          <>
            {tags.length === 0 ? (
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontStyle: 'italic' }}>
                (태그 없음)
              </span>
            ) : (
              tags.map((t) => (
                <span
                  key={t}
                  onClick={() => onTagClick(t)}
                  title={`#${t} 로 필터`}
                  style={{
                    fontSize: '11px', fontWeight: 600,
                    padding: '2px 8px', borderRadius: '10px',
                    background: activeTag === t ? '#3b82f6' : 'rgba(59,130,246,0.12)',
                    color:      activeTag === t ? '#fff'    : '#3b82f6',
                    cursor: 'pointer',
                  }}>
                  #{t}
                </span>
              ))
            )}
            {isOwner && (
              <button
                onClick={onStartEdit}
                style={{
                  background: 'none', border: '1px dashed var(--border-color)',
                  color: 'var(--text-muted)', borderRadius: '10px',
                  fontSize: '10px', padding: '1px 8px', cursor: 'pointer',
                }}>
                <FaPlus style={{ fontSize: '8px' }} /> 태그 추가
              </button>
            )}
          </>
        ) : (
          <div style={{ flex: 1, position: 'relative', minWidth: '240px' }}>
            <input
              autoFocus
              type="text"
              value={editText}
              onChange={(e) => { setEditText(e.target.value); setSuggestOpen(true) }}
              onFocus={() => setSuggestOpen(true)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') { e.preventDefault(); onSave() }
                if (e.key === 'Escape') { e.preventDefault(); onCancel() }
              }}
              placeholder="태그를 콤마로 구분 (예: 성능, SLA위반, DB)"
              style={{
                width: '100%', fontSize: '12px',
                padding: '4px 8px', border: '1px solid var(--accent)',
                borderRadius: '6px', background: 'var(--bg-secondary)',
                color: 'var(--text-primary)',
              }} />

            {suggestOpen && filteredSuggest.length > 0 && (
              <div style={{
                position: 'absolute', top: '100%', left: 0, right: 0,
                marginTop: '2px', zIndex: 20,
                background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
                borderRadius: '6px', maxHeight: '180px', overflowY: 'auto',
                boxShadow: '0 4px 8px rgba(0,0,0,0.1)',
              }}>
                {filteredSuggest.map((t) => (
                  <div
                    key={t.tag}
                    onClick={() => appendTag(t.tag)}
                    style={{
                      padding: '5px 10px', fontSize: '11px',
                      cursor: 'pointer', display: 'flex',
                      justifyContent: 'space-between', gap: '6px',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-primary)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}>
                    <span>#{t.tag}</span>
                    <span style={{ color: 'var(--text-muted)' }}>{t.count}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {isEditing && (
          <div style={{ display: 'flex', gap: '4px' }}>
            <button
              onClick={onSave}
              title="저장 (Enter)"
              style={{
                background: 'var(--accent)', color: '#fff', border: 'none',
                fontSize: '10px', padding: '3px 10px', borderRadius: '4px', cursor: 'pointer',
              }}>
              <FaCheck style={{ fontSize: '9px' }} /> 저장
            </button>
            <button
              onClick={onCancel}
              title="취소 (Esc)"
              style={{
                background: 'transparent', color: 'var(--text-muted)',
                border: '1px solid var(--border-color)',
                fontSize: '10px', padding: '3px 10px', borderRadius: '4px', cursor: 'pointer',
              }}>
              <FaTimes style={{ fontSize: '9px' }} /> 취소
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function ReviewStatusBadge({ status, large = false }: { status?: string; large?: boolean }) {
  const s = status || 'PENDING'
  const cfg = s === 'ACCEPTED'
    ? { icon: <FaCheckCircle />, color: '#10b981', bg: 'rgba(16,185,129,0.12)', label: '승인됨' }
    : s === 'REJECTED'
      ? { icon: <FaTimesCircle />, color: '#ef4444', bg: 'rgba(239,68,68,0.12)', label: '거절됨' }
      : { icon: <FaClock />, color: '#f59e0b', bg: 'rgba(245,158,11,0.12)', label: '검토 대기' }
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '4px',
      padding: large ? '4px 10px' : '2px 8px',
      borderRadius: '12px', fontSize: large ? '12px' : '11px', fontWeight: 600,
      color: cfg.color, background: cfg.bg, border: `1px solid ${cfg.color}`,
      flexShrink: 0,
    }}>
      {cfg.icon} {cfg.label}
    </span>
  )
}

/**
 * v4.2.7 — 댓글 본문을 렌더하면서 `@username` 토큰을 accent 배지로 강조.
 * 정규식은 백엔드 `ReviewCommentController.MENTION_PATTERN` 과 일치시킨다.
 */
function renderCommentContent(text: string): React.ReactNode[] {
  if (!text) return []
  // 선행 경계(문자열 시작 또는 공백) + @token 을 분리해서 span 으로 감싸기.
  // split 으로 하면 공백을 어떻게 다룰지 복잡해지므로 regex exec 로 순회.
  const out: React.ReactNode[] = []
  const re = /(^|\s)@([A-Za-z0-9_.\-]+)/g
  let lastIdx = 0
  let match: RegExpExecArray | null
  let keyIdx = 0
  while ((match = re.exec(text)) !== null) {
    const start = match.index + match[1].length // `@` 위치
    // 이전 텍스트
    if (start > lastIdx) out.push(text.substring(lastIdx, start))
    out.push(
      <span
        key={`m${keyIdx++}`}
        style={{
          display: 'inline-block',
          padding: '0 6px',
          margin: '0 1px',
          borderRadius: '10px',
          background: 'var(--accent-subtle)',
          color: 'var(--accent)',
          fontWeight: 700,
          fontSize: '11px',
        }}
      >
        @{match[2]}
      </span>
    )
    lastIdx = re.lastIndex
  }
  if (lastIdx < text.length) out.push(text.substring(lastIdx))
  return out
}

function CommentBox({ comment, isReply = false }: { comment: Comment; isReply?: boolean }) {
  // v4.2.7: createdAtIso 가 있으면 상대 시간("5분 전"), hover 시 원본 툴팁
  const relative = comment.createdAtIso ? formatRelative(comment.createdAtIso) : comment.createdAt
  const tooltip  = comment.createdAtIso ? formatDate(comment.createdAtIso) : comment.createdAt
  return (
    <div style={{ display: 'flex', gap: '8px', padding: '8px 10px', background: isReply ? 'var(--bg-primary)' : 'var(--bg-secondary)', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
      <div style={{ width: '28px', height: '28px', borderRadius: '50%', background: 'var(--accent-subtle)', color: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '11px', fontWeight: 700, flexShrink: 0 }}>
        {comment.username.substring(0, 2).toUpperCase()}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
          {comment.username}
          <span style={{ fontWeight: 400, color: 'var(--text-muted)', fontSize: '10px' }} title={tooltip}>{relative}</span>
        </div>
        <div style={{ fontSize: '12px', color: 'var(--text-sub)', marginTop: '2px', whiteSpace: 'pre-wrap' }}>
          {renderCommentContent(comment.content)}
        </div>
      </div>
    </div>
  )
}

// formatDate 는 utils/date.ts 로 이동 (v4.2.7)

const cardStyle: React.CSSProperties = {
  background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
  borderRadius: '10px', overflow: 'hidden',
}
const badgeStyle: React.CSSProperties = {
  fontSize: '11px', padding: '2px 8px', borderRadius: '4px',
  background: 'var(--accent-subtle)', color: 'var(--accent)', flexShrink: 0,
}
const iconBtnStyle: React.CSSProperties = {
  background: 'none', border: 'none', color: 'var(--text-muted)',
  cursor: 'pointer', padding: '4px', fontSize: '12px',
}
const btnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '6px',
  padding: '6px 14px', borderRadius: '6px', fontSize: '13px',
  border: '1px solid var(--border-color)', background: 'transparent',
  color: 'var(--text-sub)', cursor: 'pointer',
}
const reviewBarStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '10px', padding: '10px 12px',
  background: 'var(--bg-primary)', border: '1px solid var(--border-color)', borderRadius: '8px',
  flexWrap: 'wrap',
}
const actionBtnStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  padding: '6px 14px', borderRadius: '6px', fontSize: '12px',
  border: '1px solid', cursor: 'pointer', fontWeight: 600,
}
const commentInputStyle: React.CSSProperties = {
  padding: '6px 10px', fontSize: '12px',
  border: '1px solid var(--border-color)', borderRadius: '6px',
  background: 'var(--bg-primary)', color: 'var(--text-primary)',
}
const commentSendStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: '4px',
  padding: '6px 12px', borderRadius: '6px', fontSize: '12px',
  border: 'none', background: 'var(--accent)', color: '#fff', cursor: 'pointer',
}
