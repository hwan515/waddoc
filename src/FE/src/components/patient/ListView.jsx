import React, { useState } from 'react';
import PrescriptionModal from './PrescriptionModal';
import OpinionModal from './OpinionModal';

const headerCellClass =
    'px-8 py-5 text-center text-base lg:text-lg font-bold border-r border-white/20 last:border-r-0';
const bodyCellClass = 'px-8 py-5 text-center border-r border-slate-200 last:border-r-0';
const bodyTextClass = 'text-base lg:text-lg font-bold';
const emptyTextClass = 'text-base lg:text-lg font-medium text-slate-300';
const actionButtonBaseClass =
    'mx-auto inline-flex items-center justify-center rounded-full border px-5 py-2.5 text-base font-bold shadow-sm transition-all active:translate-y-[1px]';

const ListView = ({ records, patientName }) => {
    const [selectedPrescription, setSelectedPrescription] = useState(null);
    const [selectedOpinion, setSelectedOpinion] = useState(null);

    return (
        <div className="flex-1 flex flex-col p-7 lg:p-8 w-full bg-white animate-fade-in">
            <div className="w-full overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
                <table className="w-full min-w-[60rem] border-collapse">
                    <thead>
                        <tr className="bg-primary text-white">
                            <th className={headerCellClass}>진료일</th>
                            <th className={headerCellClass}>진료과</th>
                            <th className={headerCellClass}>담당의</th>
                            <th className={headerCellClass}>처방전</th>
                            <th className={headerCellClass}>소견서</th>
                        </tr>
                    </thead>
                    <tbody>
                        {records.length > 0 ? (
                            records.map((record, index) => (
                                <tr
                                    key={record.id || index}
                                    className={`${index % 2 === 0 ? 'bg-white' : 'bg-slate-50'} border-b border-slate-200 transition-colors hover:bg-secondary/20 last:border-b-0`}
                                >
                                    <td className={bodyCellClass}>
                                        <span className={`${bodyTextClass} text-slate-700`}>
                                            {record.date.replace(/-/g, '/')}
                                        </span>
                                    </td>
                                    <td className={bodyCellClass}>
                                        <span className={`${bodyTextClass} text-primary`}>
                                            {record.department}
                                        </span>
                                    </td>
                                    <td className={bodyCellClass}>
                                        <span className={`${bodyTextClass} text-slate-700`}>
                                            {record.doctorName}
                                        </span>
                                    </td>
                                    <td className={bodyCellClass}>
                                        {record.hasPrescription ? (
                                            <button
                                                onClick={() => setSelectedPrescription(record)}
                                                className={`${actionButtonBaseClass} border-primary text-primary hover:bg-primary hover:text-white`}
                                            >
                                                열람
                                            </button>
                                        ) : (
                                            <span className={emptyTextClass}>-</span>
                                        )}
                                    </td>
                                    <td className={bodyCellClass}>
                                        {record.hasNote ? (
                                            <button
                                                onClick={() => setSelectedOpinion(record)}
                                                className={`${actionButtonBaseClass} border-teal-800 text-teal-800 hover:bg-teal-800 hover:text-white`}
                                            >
                                                열람
                                            </button>
                                        ) : (
                                            <span className={emptyTextClass}>-</span>
                                        )}
                                    </td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan="5" className="px-8 py-10 text-center text-base lg:text-lg text-slate-500">
                                    조회된 진료 기록이 없습니다.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

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
