package com.konakart.app;

import com.konakart.appif.*;

/**
 *  The KonaKart Custom Engine - GetBundlesThatProductBelongsTo - Generated by CreateKKCustomEng
 */
@SuppressWarnings("all")
public class GetBundlesThatProductBelongsTo
{
    KKEng kkEng = null;

    /**
     * Constructor
     */
     public GetBundlesThatProductBelongsTo(KKEng _kkEng)
     {
         kkEng = _kkEng;
     }

     public ProductsIf getBundlesThatProductBelongsTo(String sessionId, DataDescriptorIf dataDesc, int productId, int languageId, FetchProductOptionsIf options) throws KKException
     {
         return kkEng.getBundlesThatProductBelongsTo(sessionId, dataDesc, productId, languageId, options);
     }
}