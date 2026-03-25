import React from 'react';

const Finish = () => {
    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-8 bg-dark font-sans relative overflow-hidden">
            {/* Background Decorations */}
            <div className="absolute top-1/4 left-0 w-96 h-96 bg-primary rounded-full mix-blend-screen filter blur-[150px] opacity-40"></div>
            <div className="absolute bottom-1/4 right-0 w-96 h-96 bg-secondary rounded-full mix-blend-screen filter blur-[150px] opacity-10"></div>

            <div className="relative z-10 w-full max-w-4xl text-center space-y-10 mb-16 animate-fade-in-up">
                <h1 className="text-4xl md:text-6xl font-bold text-white tracking-tight leading-tight mb-8">
                    진료가 종료되었습니다.
                </h1>
                <br />

                <div className="text-xl md:text-3xl text-secondary font-medium leading-relaxed space-y-2">
                    <p>진료를 통해 처방 받으신 약은</p>
                    <p>
                        <span className="text-white font-bold">금일 오후 8~10시</span> 사이에 배송 예정입니다.
                    </p>
                    <p>자세한 사항은 문자로 안내됩니다.</p>
                </div>
            </div>
        </div>
    );
};

export default Finish;
