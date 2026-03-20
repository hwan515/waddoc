import React, { useState, useEffect } from 'react';
import apiClient from '../../utils/api';
import { UserPlus, Search, X } from 'lucide-react';

const PatientManagement = () => {
    const [patients, setPatients] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    
    // Modal & Form state
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState({
        name: '',
        birthDate: '',
        phone: '',
        gender: 'UNKNOWN',
        regionCode: '',
        address: ''
    });
    // referenceImage is handled separately to avoid React controlling a file input directly
    const [imageFile, setImageFile] = useState(null);

    const fetchPatients = async () => {
        try {
            setLoading(true);
            const response = await apiClient.get('/admin/patients', { params: { size: 100 } });
            // Assuming response.data.patients
            setPatients(response.data.patients || []);
        } catch (error) {
            console.error("Failed to fetch patients:", error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPatients();
    }, []);

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            setImageFile(e.target.files[0]);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            const submitData = new FormData();
            submitData.append('name', formData.name);
            submitData.append('birthDate', formData.birthDate);
            submitData.append('phone', formData.phone);
            submitData.append('gender', formData.gender);
            submitData.append('regionCode', formData.regionCode);
            if (formData.address) submitData.append('address', formData.address);
            if (imageFile) submitData.append('referenceImage', imageFile);

            await apiClient.post('/patients', submitData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            
            alert('환자 등록이 완료되었습니다.');
            setIsModalOpen(false);
            setFormData({ name: '', birthDate: '', phone: '', gender: 'UNKNOWN', regionCode: '', address: '' });
            setImageFile(null);
            fetchPatients(); // Refresh list
        } catch (error) {
            console.error("Failed to register patient:", error);
            alert('환자 등록에 실패했습니다.');
        }
    };

    const filteredPatients = patients
        .filter(p => 
            (p.name && p.name.includes(searchTerm)) || 
            (p.phone && p.phone.includes(searchTerm))
        )
        .sort((a, b) => {
            const idA = String(a.patientId || '');
            const idB = String(b.patientId || '');
            return idA.localeCompare(idB);
        });

    return (
        <div className="p-6 h-full flex flex-col bg-[#F5F6F8] overflow-hidden">
            <div className="flex justify-between items-center mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-800">환자 관리</h1>
                    <p className="text-sm text-gray-500 mt-1">등록된 전체 환자 목록을 조회하고 새로운 환자를 등록합니다.</p>
                </div>
                <button 
                    onClick={() => setIsModalOpen(true)}
                    className="flex items-center gap-2 bg-[#0353A4] text-white px-4 py-2 rounded-lg font-medium hover:bg-[#023E7A] transition-colors shadow-sm"
                >
                    <UserPlus className="w-5 h-5" />
                    새로운 환자 등록
                </button>
            </div>
            
            <div className="flex-1 bg-white border border-gray-200 rounded-xl flex flex-col overflow-hidden shadow-sm">
                <div className="p-4 border-b border-gray-100 flex items-center justify-between">
                    <div className="relative w-64">
                        <input 
                            type="text" 
                            placeholder="이름 또는 전화번호 검색..." 
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0353A4]/50"
                        />
                        <Search className="w-4 h-4 text-gray-400 absolute left-3 top-2.5" />
                    </div>
                </div>

                <div className="flex-1 overflow-auto">
                    {loading ? (
                        <div className="h-full flex items-center justify-center text-gray-400">
                            데이터를 불러오는 중입니다...
                        </div>
                    ) : (
                        <table className="w-full text-left border-collapse min-w-[800px]">
                            <thead className="bg-gray-50 sticky top-0 text-sm font-semibold text-gray-600">
                                <tr>
                                    <th className="p-4 border-b w-[15%]">환자 ID</th>
                                    <th className="p-4 border-b w-[15%]">이름</th>
                                    <th className="p-4 border-b w-[20%]">연락처</th>
                                    <th className="p-4 border-b w-[20%]">생년월일</th>
                                    <th className="p-4 border-b w-[10%]">성별</th>
                                    <th className="p-4 border-b w-[20%]">지역코드</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredPatients.length === 0 ? (
                                    <tr>
                                        <td colSpan="6" className="p-8 text-center text-gray-500">조건에 맞는 환자가 없습니다.</td>
                                    </tr>
                                ) : (
                                    filteredPatients.map((patient) => (
                                        <tr key={patient.patientId} className="border-b border-gray-100 hover:bg-gray-50">
                                            <td className="p-4 text-sm text-gray-500 truncate">{patient.patientId}</td>
                                            <td className="p-4 text-sm font-medium text-gray-800 truncate">{patient.name}</td>
                                            <td className="p-4 text-sm text-gray-600 truncate">{patient.phone}</td>
                                            <td className="p-4 text-sm text-gray-600 truncate">{patient.birthDate6 || patient.birthDate}</td>
                                            <td className="p-4 text-sm text-gray-600 truncate">
                                                {String(patient.gender).toUpperCase() === 'MALE' ? '남성' : String(patient.gender).toUpperCase() === 'FEMALE' ? '여성' : '미상'}
                                            </td>
                                            <td className="p-4 text-sm text-gray-600 truncate">{patient.regionCode}</td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            {/* Registration Modal */}
            {isModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
                    <div className="bg-white rounded-xl shadow-lg w-full max-w-lg overflow-hidden flex flex-col max-h-[90vh]">
                        <div className="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                            <h2 className="text-xl font-bold text-gray-800">새로운 환자 등록</h2>
                            <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600">
                                <X className="w-5 h-5" />
                            </button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto flex-1 flex flex-col gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">이름 *</label>
                                <input required type="text" name="name" value={formData.name} onChange={handleInputChange} className="w-full p-2 border rounded-lg" placeholder="홍길동"/>
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">생년월일 *</label>
                                    <input required type="date" name="birthDate" value={formData.birthDate} onChange={handleInputChange} className="w-full p-2 border rounded-lg"/>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">성별 *</label>
                                    <select name="gender" value={formData.gender} onChange={handleInputChange} className="w-full p-2 border rounded-lg bg-white">
                                        <option value="MALE">남성</option>
                                        <option value="FEMALE">여성</option>
                                        <option value="UNKNOWN">미상</option>
                                    </select>
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">전화번호 *</label>
                                <input required type="text" name="phone" value={formData.phone} onChange={handleInputChange} className="w-full p-2 border rounded-lg" placeholder="01012345678"/>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">지역 코드 *</label>
                                <input required type="text" name="regionCode" value={formData.regionCode} onChange={handleInputChange} className="w-full p-2 border rounded-lg" placeholder="GIMCHEON_JEUNGSAN"/>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">주소</label>
                                <input type="text" name="address" value={formData.address} onChange={handleInputChange} className="w-full p-2 border rounded-lg" placeholder="상세 주소 입력"/>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">기준 얼굴 이미지</label>
                                <input type="file" accept="image/jpeg,image/png" onChange={handleFileChange} className="w-full p-2 text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-[#0353A4] hover:file:bg-blue-100" />
                            </div>
                            <div className="mt-4 flex justify-end gap-2">
                                <button type="button" onClick={() => setIsModalOpen(false)} className="px-4 py-2 text-sm font-medium text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200">취소</button>
                                <button type="submit" className="px-4 py-2 text-sm font-medium text-white bg-[#0353A4] rounded-lg hover:bg-[#023E7A]">등록하기</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default PatientManagement;
