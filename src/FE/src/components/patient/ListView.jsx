import React, { useState } from 'react';
import PrescriptionModal from './PrescriptionModal';
import OpinionModal from './OpinionModal';

const ListView = ({ records, patientName }) => {
    const [selectedPrescription, setSelectedPrescription] = useState(null);
    const [selectedOpinion, setSelectedOpinion] = useState(null);
    return (
        <div className="flex-1 flex flex-col p-6 animate-fade-in w-full bg-white">
            <div className="w-full overflow-hidden border border-slate-200 rounded-2xl shadow-sm bg-white">
                <table className="w-full border-collapse">
                    <thead>
                        <tr className="bg-primary text-white">
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-white/20">진료일</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-white/20">진료과목</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-white/20">담당의</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-white/20">처방전 보기</th>
                            <th className="px-6 py-4 text-center text-sm font-bold">소견서 보기</th>
                        </tr>
                    </thead>
                    <tbody>
                        {records.length > 0 ? records.map((r, i) => (
                            <tr key={r.id || i} className={`${i % 2 === 0 ? 'bg-white' : 'bg-slate-50'} hover:bg-secondary/20 transition-colors border-b border-slate-200 last:border-b-0`}>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <span className="text-sm font-bold text-slate-700">{r.date.replace(/-/g, '/')}</span>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <span className="text-sm font-bold text-primary">{r.department}</span>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <span className="text-sm font-bold text-slate-700">{r.doctorName}</span>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    {r.hasPrescription ? (
                                        <button 
                                            onClick={() => setSelectedPrescription(r)}
                                            className="mx-auto rounded-full border border-primary px-4 py-1.5 text-sm font-bold text-primary shadow-sm transition-all hover:bg-primary hover:text-white active:translate-y-[1px]"
                                        >
                                            처방전 보기
                                        </button>
                                    ) : (
                                        <span className="text-sm font-medium text-slate-300">-</span>
                                    )}
                                </td>
                                <td className="px-6 py-4 text-center">
                                    {r.hasNote ? (
                                        <button 
                                            onClick={() => setSelectedOpinion(r)}
                                            className="text-sm font-bold text-[#115E59] hover:text-white hover:bg-[#115E59] border border-[#115E59] px-4 py-1.5 rounded-full transition-all mx-auto shadow-sm active:translate-y-[1px]"
                                        >
                                            소견서 보기
                                        </button>
                                    ) : (
                                        <span className="text-sm font-medium text-slate-300">-</span>
                                    )}
                                </td>
                            </tr>
                        )) : (
                            <tr>
                                <td colSpan="5" className="px-6 py-8 text-center text-slate-500">
                                    조회된 진료 기록이 없습니다.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

            {/* Modals */}
            <PrescriptionModal 
                isOpen={!!selectedPrescription} 
                onClose={() => setSelectedPrescription(null)} 
                record={selectedPrescription} 
                patientName={patientName}
            />
            <OpinionModal 
                isOpen={!!selectedOpinion} 
                onClose={() => setSelectedOpinion(null)} 
                record={selectedOpinion} 
                patientName={patientName}
            />
        </div>
    );
};

export default ListView;
