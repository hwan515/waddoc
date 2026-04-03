import React from 'react';
import { createPortal } from 'react-dom';
import { X, FileSignature, Printer } from 'lucide-react';

/**
 * 소견서(Medical Opinion) 모달 컴포넌트
 * 환자의 진료 후 의사 소견 및 요약을 병원 문서 스타일로 렌더링합니다.
 */
const OpinionModal = ({ isOpen, onClose, record, patientName }) => {
    if (!isOpen || !record || typeof document === 'undefined') return null;

    // 모달 배경 클릭 시 닫기
    const handleBackdropClick = (e) => {
        if (e.target === e.currentTarget) {
            onClose();
        }
    };

    const modalContent = (
        <div 
            className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm animate-fade-in"
            onClick={handleBackdropClick}
        >
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-slide-up">
                
                {/* 헤더 영역 */}
                <div className="bg-teal-800 px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-2 text-white">
                        <FileSignature className="w-5 h-5" />
                        <h2 className="text-lg font-bold">진료 소견서</h2>
                    </div>
                    <div className="flex items-center gap-3">
                        <button className="text-white/80 hover:text-white transition-colors flex items-center gap-1 text-sm bg-white/10 px-3 py-1.5 rounded-lg">
                            <Printer className="w-4 h-4" />
                            <span>출력</span>
                        </button>
                        <button 
                            onClick={onClose}
                            className="text-white/80 hover:text-white transition-colors p-1"
                        >
                            <X className="w-6 h-6" />
                        </button>
                    </div>
                </div>

                {/* 내용 영역 (스크롤 가능) */}
                <div className="p-3 md:p-4 overflow-y-auto flex-1 bg-slate-50 text-black">
                    {/* 문서 실제 종이 양식 컨테이너 */}
                    <div className="bg-white border-2 border-slate-300 p-4 md:p-5 shadow-sm">
                        
                        {/* 폼 타이틀 */}
                        <div className="flex justify-between items-start mb-3 border-b-[3px] border-slate-800 pb-2">
                            <div className="flex flex-col">
                                <span className="text-xs md:text-sm font-bold mb-1">진단 및 소견 요약정보</span>
                                <h1 className="text-xl md:text-2xl font-extrabold tracking-widest text-black">소 견 서</h1>
                            </div>
                            <div className="text-right">
                                <h2 className="text-sm md:text-base font-bold text-black tracking-tight">Waddoc 중앙의료원</h2>
                                <p className="text-[10px] md:text-xs text-black">도서산간 비대면 원격진료센터</p>
                            </div>
                        </div>

                        {/* 환자 및 진료 기본 정보 */}
                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-0 border border-slate-400 text-xs md:text-sm mb-3">
                            <div className="bg-slate-100 p-2 md:p-3 font-bold border-r border-b border-slate-400 flex items-center text-black">진료일자</div>
                            <div className="p-2 md:p-3 border-r border-b border-slate-400 font-medium text-black">
                                {record.date.replace(/-/g, '.')}
                            </div>
                            
                            <div className="bg-slate-100 p-2 md:p-3 font-bold border-r border-b border-slate-400 flex items-center text-black">진료과목</div>
                            <div className="p-2 md:p-3 border-b border-slate-400 text-black font-bold">
                                {record.department}
                            </div>

                            <div className="bg-slate-100 p-2 md:p-3 font-bold border-r border-slate-400 flex items-center text-black">환자 성명</div>
                            <div className="p-2 md:p-3 border-r border-slate-400 font-bold text-black">
                                {patientName && patientName !== '불러오는 중...' && patientName !== '연결된 환자 없음' ? patientName : '이름 미상'}
                            </div>

                            <div className="bg-slate-100 p-2 md:p-3 font-bold border-r border-slate-400 flex items-center text-black">담당 의사</div>
                            <div className="p-2 md:p-3 font-bold text-black flex items-center justify-between">
                                <span>{record.doctorName}</span>
                                <div className="w-8 h-8 rounded-full border-2 border-red-500 text-red-500 flex items-center justify-center text-[10px] font-bold opacity-80 transform -rotate-[15deg] ml-2 shrink-0">
                                    서명
                                </div>
                            </div>
                        </div>

                        {/* 소견 내용 작성란 */}
                        <div className="flex flex-col mt-2 md:mt-3">
                            <div className="flex items-center gap-2 mb-1 md:mb-2">
                                <div className="w-1.5 h-1.5 md:w-2 md:h-2 rounded-full bg-black"></div>
                                <h3 className="font-bold text-black text-xs md:text-sm">진단 내용 및 의사 소견</h3>
                            </div>
                            <div className="border border-slate-400 min-h-[100px] md:min-h-[120px] p-3 md:p-4 text-black text-xs md:text-sm leading-relaxed whitespace-pre-wrap font-medium flex flex-col bg-white">
                                {record.summaryNote ? record.summaryNote : "등록된 소견 내용이 없습니다."}
                            </div>
                        </div>

                        <div className="mt-3 md:mt-4 text-center text-[11px] md:text-sm font-semibold text-black">
                            상기와 같이 진단 및 소견을 발급합니다.
                        </div>

                    </div>
                </div>

                {/* 푸터 버튼 영역 */}
                <div className="bg-white p-4 border-t border-slate-200 flex justify-end">
                    <button 
                        onClick={onClose}
                        className="px-6 py-2 bg-slate-800 hover:bg-slate-700 text-white font-bold rounded-lg transition-colors shadow-sm"
                    >
                        닫기
                    </button>
                </div>

            </div>
        </div>
    );

    return createPortal(modalContent, document.body);
};

export default OpinionModal;
