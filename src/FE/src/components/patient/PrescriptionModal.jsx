import React from 'react';
import { createPortal } from 'react-dom';
import { X, FileText, Printer } from 'lucide-react';
import { parsePrescriptionNote } from '../../utils/prescriptionNote';

const BARCODE_WIDTHS = [
    'w-1', 'w-0.5', 'w-1', 'w-1', 'w-0.5',
    'w-1', 'w-0.5', 'w-0.5', 'w-1', 'w-0.5',
    'w-1', 'w-1', 'w-0.5', 'w-1', 'w-0.5',
    'w-1', 'w-0.5', 'w-1', 'w-1', 'w-0.5',
];

/**
 * 처방전(Prescription) 모달 컴포넌트
 * 실제 병원 처방전 양식과 유사한 느낌으로 디자인되었습니다.
 */
const PrescriptionModal = ({ isOpen, onClose, record, patientName }) => {
    if (!isOpen || !record || typeof document === 'undefined') return null;

    const prescription = record.prescription ?? parsePrescriptionNote(record.prescriptionNote);

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
                <div className="bg-primary px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-2 text-white">
                        <FileText className="w-5 h-5" />
                        <h2 className="text-lg font-bold">처방전 (환자보관용)</h2>
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
                    {/* 처방전 실제 종이 양식 컨테이너 */}
                    <div className="bg-white border-2 border-slate-300 p-4 md:p-5 shadow-sm">
                        
                        {/* 병원명 및 폼 타이틀 */}
                        <div className="text-center pb-2 border-b-2 border-slate-800 mb-3 relative">
                            <h1 className="text-xl md:text-2xl font-bold tracking-widest text-black mb-1">처 방 전</h1>
                            <p className="text-xs font-semibold text-black">Waddoc 비대면 의료센터</p>
                            
                            {/* 우측 바코드 가짜 UI */}
                            <div className="absolute right-0 top-0 hidden sm:flex flex-col items-end">
                                <div className="flex gap-0.5 h-8 w-20 md:h-10 md:w-24">
                                    {BARCODE_WIDTHS.map((widthClass, i) => (
                                        <div key={i} className={`bg-black h-full ${widthClass}`}></div>
                                    ))}
                                </div>
                                <span className="text-[10px] mt-1 tracking-widest text-black">
                                    {record.id || 'NO_CASE_ID'}
                                </span>
                            </div>
                        </div>

                        {/* 의료기관 및 처방의 정보 */}
                        <div className="flex flex-col sm:flex-row gap-2 mb-3">
                            <table className="w-full sm:w-1/2 border-collapse border border-slate-400 text-xs md:text-sm">
                                <tbody>
                                    <tr>
                                        <th className="border border-slate-400 bg-slate-100 py-1.5 px-3 md:py-2 text-left w-24 font-bold text-black">교부일자</th>
                                        <td className="border border-slate-400 py-1.5 px-3 md:py-2 text-black font-medium">
                                            {record.date}
                                        </td>
                                    </tr>
                                    <tr>
                                        <th className="border border-slate-400 bg-slate-100 py-1.5 px-3 md:py-2 text-left w-24 font-bold text-black">환자 성명</th>
                                        <td className="border border-slate-400 py-1.5 px-3 md:py-2 text-black font-bold">
                                            {patientName && patientName !== '불러오는 중...' && patientName !== '연결된 환자 없음' ? patientName : '이름 미상'}
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                            
                            <table className="w-full sm:w-1/2 border-collapse border border-slate-400 text-sm">
                                <tbody>
                                    <tr>
                                        <th className="border border-slate-400 bg-slate-100 py-1.5 px-3 md:py-2 text-left w-24 font-bold text-black">진료과목</th>
                                        <td className="border border-slate-400 py-1.5 px-3 md:py-2 text-black font-bold">
                                            {record.department}
                                        </td>
                                    </tr>
                                    <tr>
                                        <th className="border border-slate-400 bg-slate-100 py-1.5 px-3 md:py-2 text-left w-24 font-bold text-black">담당 의사</th>
                                        <td className="border border-slate-400 py-1.5 px-3 md:py-2 text-black flex justify-between items-center">
                                            <span>{record.doctorName} (인)</span>
                                            {/* 가짜 도장 */}
                                            <div className="w-8 h-8 rounded-full border-2 border-red-500 text-red-500 flex items-center justify-center text-[10px] font-bold opacity-80 transform -rotate-12 translate-x-1">
                                                서명
                                            </div>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        {/* 처방 내역 */}
                        <div className="border border-slate-400">
                            <div className="bg-slate-100 border-b border-slate-400 py-1.5 px-3 md:py-2 md:px-4 text-xs md:text-sm font-bold text-black">
                                처방 의약품 명칭 및 상세 내역
                            </div>
                            <div className="p-3 md:p-4 min-h-[80px] md:min-h-[120px] text-black text-xs md:text-sm leading-relaxed font-medium">
                                {prescription.isStructured && prescription.items.length > 0 ? (
                                    <div className="space-y-2">
                                        {prescription.items.map((item, index) => (
                                            <div
                                                key={`${item.code}-${index}`}
                                                className="rounded-lg border border-slate-200 bg-slate-50 p-3"
                                            >
                                                <div className="flex items-start justify-between gap-3">
                                                    <div>
                                                        <div className="font-bold text-slate-900">
                                                            {item.name}
                                                            {item.isFallback ? ' (코드 매핑 필요)' : ''}
                                                        </div>
                                                        <div className="mt-1 text-[11px] text-slate-500">
                                                            코드: {item.code}
                                                        </div>
                                                    </div>
                                                    <div className="rounded-full bg-primary/10 px-2 py-1 text-[11px] font-semibold text-primary">
                                                        {item.category}
                                                    </div>
                                                </div>
                                                <div className="mt-2 text-slate-700">
                                                    용법/용량: {item.dosage}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                ) : prescription.rawText ? (
                                    <div className="whitespace-pre-wrap">{prescription.rawText}</div>
                                ) : (
                                    "처방 내역이 존재하지 않습니다."
                                )}
                                {prescription.isStructured && prescription.items.length > 0 ? (
                                    <div className="mt-3 border-t border-dashed border-slate-300 pt-2 text-[11px] text-slate-500">
                                        총 {prescription.items.length}개 약품
                                    </div>
                                ) : null}
                            </div>
                        </div>

                        {/* 안내사항 */}
                        <div className="mt-2 text-[10px] md:text-xs text-black leading-relaxed border-t border-dashed border-slate-400 pt-2">
                            * 본 처방전은 비대면 진료를 통해 발행된 전자 처방전 요약본입니다.<br/>
                            * 약사법 제23조 및 의료법 제18조에 의거하여 작성되었습니다.<br/>
                            * 약품 교부 및 복용에 관한 상세한 설명은 약사에게 받으시기 바랍니다.
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

export default PrescriptionModal;
