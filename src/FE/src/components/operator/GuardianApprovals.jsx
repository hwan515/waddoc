import React, { useState, useEffect } from 'react';
import apiClient from '../../utils/api';
import { Check, X, ShieldAlert } from 'lucide-react';

const GuardianApprovals = () => {
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);

    const [modalConfig, setModalConfig] = useState({ isOpen: false, action: null, linkId: null, comment: '' });

    const fetchRequests = async () => {
        try {
            setLoading(true);
            const response = await apiClient.get('/admin/guardian-link-requests', { 
                params: { status: 'PENDING', size: 100 } 
            });
            setRequests(response.data.requests || []);
        } catch (error) {
            console.error("Failed to fetch guardian requests:", error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchRequests();
    }, []);

    const openModal = (action, linkId) => {
        setModalConfig({ isOpen: true, action, linkId, comment: action === 'approve' ? '정상 등록 확인' : '정보 불일치' });
    };

    const submitModal = async () => {
        const { action, linkId, comment } = modalConfig;
        if (!linkId) return;
        
        try {
            if (action === 'approve') {
                await apiClient.post(`/admin/guardian-link-requests/${linkId}/approve`, { comment });
            } else {
                await apiClient.post(`/admin/guardian-link-requests/${linkId}/reject`, { reason: comment });
            }
            setModalConfig({ isOpen: false, action: null, linkId: null, comment: '' });
            fetchRequests();
        } catch (error) {
            console.error(`Failed to ${action} request:`, error);
            alert("처리에 실패했습니다.");
        }
    };

    return (
        <div className="p-6 h-full flex flex-col bg-[#F5F6F8] overflow-hidden">
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-gray-800">보호자 가입 승인 대기</h1>
                <p className="text-sm text-gray-500 mt-1">보호자 앱에서 가입을 요청한 목록입니다. 정보 확인 후 승인해 주세요.</p>
            </div>
            
            <div className="flex-1 overflow-auto">
                {loading ? (
                    <div className="h-full flex items-center justify-center text-gray-400 bg-white rounded-xl border border-gray-200">
                        데이터를 불러오는 중입니다...
                    </div>
                ) : requests.length === 0 ? (
                    <div className="h-full flex flex-col items-center justify-center text-gray-400 bg-white rounded-xl border border-gray-200">
                        <ShieldAlert className="w-12 h-12 text-gray-200 mb-4" />
                        <p>현재 대기 중인 승인 요청이 없습니다.</p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {requests.map((req) => (
                            <div key={req.linkId} className="bg-white border text-left border-gray-200 rounded-xl p-5 shadow-sm hover:shadow-md transition-shadow">
                                <div className="flex justify-between items-start mb-4">
                                    <div className="bg-yellow-100 text-yellow-700 text-xs font-bold px-2.5 py-1 rounded">승인 대기</div>
                                    <div className="text-xs text-gray-400">{new Date(req.requestedAt).toLocaleString()}</div>
                                </div>
                                
                                <div className="space-y-3 mb-6">
                                    <div>
                                        <div className="text-xs text-gray-500 font-medium">신청자 (보호자)</div>
                                        <div className="text-sm font-bold text-gray-800">{req.guardianName} <span className="text-gray-400 font-normal">({req.guardianUserId})</span></div>
                                    </div>
                                    <div className="p-3 bg-blue-50/50 rounded-lg border border-blue-100">
                                        <div className="text-xs text-primary font-medium mb-1">대상 환자 정보</div>
                                        <div className="text-sm font-bold text-gray-800">{req.patientName}</div>
                                        <div className="text-xs text-gray-600">{req.patientPhone}</div>
                                        <div className="text-xs font-semibold text-gray-800 mt-1">관계: {req.relation}</div>
                                    </div>
                                </div>

                                <div className="flex gap-2 w-full mt-auto">
                                    <button 
                                        onClick={() => openModal('reject', req.linkId)}
                                        className="flex-1 flex items-center justify-center gap-1.5 py-2 text-sm font-bold text-red-600 bg-red-50 hover:bg-red-100 rounded-lg transition-colors border border-red-100">
                                        <X className="w-4 h-4" /> 반려
                                    </button>
                                    <button 
                                        onClick={() => openModal('approve', req.linkId)}
                                        className="flex-1 flex items-center justify-center gap-1.5 py-2 text-sm font-bold text-white bg-primary hover:bg-primary/90 rounded-lg transition-colors shadow-sm">
                                        <Check className="w-4 h-4" /> 승인
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* 승인/반려 모달 */}
            {modalConfig.isOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
                    <div className="bg-white rounded-xl shadow-lg w-full max-w-sm overflow-hidden flex flex-col">
                        <div className={`px-6 py-4 border-b border-gray-100 flex justify-between items-center ${modalConfig.action === 'approve' ? 'bg-blue-50 text-primary' : 'bg-red-50 text-red-600'}`}>
                            <h2 className="text-lg font-bold">
                                {modalConfig.action === 'approve' ? '가입 요청 승인' : '가입 요청 반려'}
                            </h2>
                            <button onClick={() => setModalConfig({ ...modalConfig, isOpen: false })} className="hover:opacity-70 text-gray-500">
                                <X className="w-5 h-5" />
                            </button>
                        </div>
                        <div className="p-6">
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                {modalConfig.action === 'approve' ? '승인 코멘트 (선택)' : '반려 사유 (필수)'}
                            </label>
                            <textarea 
                                value={modalConfig.comment}
                                onChange={(e) => setModalConfig({ ...modalConfig, comment: e.target.value })}
                                className="w-full p-3 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 resize-none h-24"
                                placeholder={modalConfig.action === 'approve' ? '승인 참고사항을 입력하세요' : '반려 사유를 자세히 적어주세요'}
                            />
                            <div className="mt-6 flex justify-end gap-2">
                                <button type="button" onClick={() => setModalConfig({ ...modalConfig, isOpen: false })} className="px-4 py-2 text-sm font-medium text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200">취소</button>
                                <button type="button" onClick={submitModal} className={`px-4 py-2 text-sm font-bold text-white rounded-lg transition-colors ${modalConfig.action === 'approve' ? 'bg-primary hover:bg-primary/90' : 'bg-red-600 hover:bg-red-700'}`}>
                                    {modalConfig.action === 'approve' ? '승인 완료' : '반려 완료'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default GuardianApprovals;
