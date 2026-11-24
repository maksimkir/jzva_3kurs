package com.example.bookinfo;

/** Сервіс рейтингів шукає книги лише за ISBN. */
public class MissingIsbnException extends RuntimeException {

    public MissingIsbnException() {
        super("Сервіс рейтингів шукає книги лише за ISBN, а в книги його немає");
    }
}
