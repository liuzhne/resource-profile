package com.edu.knowledge;
/** Fixed safe error; never stores remote response bodies or credentials. */
public class KnowledgeException extends RuntimeException {
    final int status;
    public KnowledgeException(int status, String message) { super(message); this.status=status; }
}
