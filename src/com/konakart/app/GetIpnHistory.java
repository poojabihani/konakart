package com.konakart.app;

import com.konakart.appif.*;

/**
 *  The KonaKart Custom Engine - GetIpnHistory - Generated by CreateKKCustomEng
 */
@SuppressWarnings("all")
public class GetIpnHistory
{
    KKEng kkEng = null;

    /**
     * Constructor
     */
     public GetIpnHistory(KKEng _kkEng)
     {
         kkEng = _kkEng;
     }

     public IpnHistoryIf[] getIpnHistory(String sessionId, int orderId) throws KKException
     {
         return kkEng.getIpnHistory(sessionId, orderId);
     }
}