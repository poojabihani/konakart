package com.konakart.app;

import com.konakart.appif.*;

/**
 *  The KonaKart Custom Engine - ReadMessageFromQueue - Generated by CreateKKCustomEng
 */
@SuppressWarnings("all")
public class ReadMessageFromQueue
{
    KKEng kkEng = null;

    /**
     * Constructor
     */
     public ReadMessageFromQueue(KKEng _kkEng)
     {
         kkEng = _kkEng;
     }

     public MqResponseIf readMessageFromQueue(String sessionId, MqOptionsIf options) throws KKException
     {
         return kkEng.readMessageFromQueue(sessionId, options);
     }
}