package com.konakart.app;

/**
 *  The KonaKart Custom Engine - SendNewPassword - Generated by CreateKKCustomEng
 */
@SuppressWarnings("all")
public class SendNewPassword
{
    KKEng kkEng = null;

    /**
     * Constructor
     */
     public SendNewPassword(KKEng _kkEng)
     {
         kkEng = _kkEng;
     }

      @Deprecated
     public void sendNewPassword(String emailAddr, String subject, String countryCode) throws KKException
     {
         kkEng.sendNewPassword(emailAddr, subject, countryCode);
     }
}