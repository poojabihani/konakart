package com.konakart.app;

import com.konakart.appif.*;

/**
 *  The KonaKart Custom Engine - EditWishListWithOptions - Generated by CreateKKCustomEng
 */
@SuppressWarnings("all")
public class EditWishListWithOptions
{
    KKEng kkEng = null;

    /**
     * Constructor
     */
     public EditWishListWithOptions(KKEng _kkEng)
     {
         kkEng = _kkEng;
     }

     public void editWishListWithOptions(String sessionId, WishListIf wishList, AddToWishListOptionsIf options) throws KKException
     {
         kkEng.editWishListWithOptions(sessionId, wishList, options);
     }
}
