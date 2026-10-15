package com.edu.knowledge;
import java.nio.charset.StandardCharsets;
import java.util.*;
final class TextChunks {
    private TextChunks() { }
    static List<String> split(String text,RagContracts.Strategy strategy) {
        text=text.strip(); if(text.isEmpty()) throw new KnowledgeException(422,"text is empty");
        List<String> result=new ArrayList<>();
        switch(strategy.name()) {
            case "none" -> result.add(text);
            case "fixed_size" -> {
                int window=strategy.window(), overlap=strategy.overlap();
                if(window<=0 || overlap<0 || overlap>=window) throw new KnowledgeException(422,"invalid window/overlap");
                int[] points=text.codePoints().toArray();
                for(int start=0;start<points.length;start+=window-overlap) {
                    result.add(new String(points,start,Math.min(window,points.length-start)));
                    if(result.size()>200) throw new KnowledgeException(422,"too many chunks");
                    if(start+window>=points.length) break;
                }
            }
            case "sentence" -> {
                int max=strategy.maxChars(); if(max<=0) throw new KnowledgeException(422,"invalid max_chars");
                String buffer="";
                for(String part:text.split("(?<=[。！？!?])|\\n\\n+")) {
                    String sentence=part.strip(); if(sentence.isEmpty()) continue;
                    int size=sentence.codePointCount(0,sentence.length());
                    if(size>max) {
                        if(!buffer.isEmpty()) {result.add(buffer);buffer="";}
                        result.addAll(split(sentence,new RagContracts.Strategy("fixed_size",max,0,max)));
                    } else if(!buffer.isEmpty() && buffer.codePointCount(0,buffer.length())+size+1>max) {
                        result.add(buffer); buffer=sentence;
                    } else buffer=buffer.isEmpty()?sentence:buffer+" "+sentence;
                    if(result.size()>200) throw new KnowledgeException(422,"too many chunks");
                }
                if(!buffer.isEmpty()) result.add(buffer);
            }
            default -> throw new KnowledgeException(422,"unknown chunk strategy");
        }
        if(result.isEmpty() || result.size()>200) throw new KnowledgeException(422,"invalid chunk count");
        for(String chunk:result) if(chunk.getBytes(StandardCharsets.UTF_8).length>4096)
            throw new KnowledgeException(422,"chunk exceeds 4096 bytes; use smaller chunks");
        return result;
    }
}
