import React from 'react';

export function AuditLogFeed({ auditLogs }) {
  return (
    <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden">
      <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-emerald-500 to-teal-500"></div>
      <h2 className="text-2xl font-bold mb-6 flex items-center">
        <svg className="w-6 h-6 mr-3 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        Real-Time Audit Log
      </h2>

      <div className="space-y-3 max-h-[400px] overflow-y-auto pr-2 custom-scrollbar">
        {auditLogs.length === 0 ? (
          <p className="text-slate-500 text-center py-8 text-sm">No audit logs available.</p>
        ) : (
          auditLogs.map((log) => (
            <div key={log.id} className="p-3 bg-slate-900 rounded-lg border border-slate-700 text-sm">
              <div className="flex justify-between items-start mb-2">
                <span className="text-xs font-mono text-emerald-400 bg-emerald-400/10 px-2 py-0.5 rounded">
                  {log.outcome}
                </span>
                <span className="text-[10px] text-slate-500">
                  {new Date(log.createdAt).toLocaleTimeString()}
                </span>
              </div>
              <p className="text-slate-300 text-xs mb-1">
                <span className="text-slate-500">Action:</span> {log.actionTaken}
              </p>
              <p className="text-slate-400 text-xs italic">"{log.reasoning}"</p>
            </div>
          ))
        )}
      </div>
    </div>
  );
}