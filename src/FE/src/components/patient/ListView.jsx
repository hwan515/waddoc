const ListView = ({ records }) => {
    return (
        <div className="flex-1 flex flex-col p-6 animate-fade-in w-full bg-white">
            <div className="w-full overflow-hidden border border-slate-200 rounded-2xl shadow-sm bg-white">
                <table className="w-full border-collapse">
                    <thead>
                        <tr className="bg-[#0353A4] text-white">
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">진료일</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">진료과목</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">담당의</th>
                            <th className="px-6 py-4 text-center text-sm font-bold border-r border-[#ffffff20]">처방전 보기</th>
                            <th className="px-6 py-4 text-center text-sm font-bold">소견서 보기</th>
                        </tr>
                    </thead>
                    <tbody>
                        {records.length > 0 ? records.map((r, i) => (
                            <tr key={r.id || i} className={`${i % 2 === 0 ? 'bg-white' : 'bg-[#F8FAFC]'} hover:bg-[#B9D6F2]/20 transition-colors border-b border-slate-200 last:border-b-0`}>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <div className="flex flex-col items-center">
                                        <span className="text-sm font-bold text-slate-700">{r.date.replace(/-/g, '/')}</span>
                                        <span className="text-xs font-semibold text-slate-500 mt-0.5">{r.time}</span>
                                    </div>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <span className="text-sm font-bold text-[#0353A4]">{r.department}</span>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    <span className="text-sm font-bold text-slate-700">{r.doctorName}</span>
                                </td>
                                <td className="px-6 py-4 border-r border-slate-200 text-center">
                                    {r.hasPrescription ? (
                                        <button className="text-sm font-bold text-[#0353A4] hover:bg-[#0353A4]/5 px-3 py-1.5 rounded-lg transition-colors mx-auto">
                                            처방전 보기
                                        </button>
                                    ) : (
                                        <span className="text-sm font-medium text-slate-300">-</span>
                                    )}
                                </td>
                                <td className="px-6 py-4 text-center">
                                    {r.hasNote ? (
                                        <button className="text-sm font-bold text-[#0353A4] hover:bg-[#0353A4]/5 px-3 py-1.5 rounded-lg transition-colors mx-auto">
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
        </div>
    );
};

export default ListView;
