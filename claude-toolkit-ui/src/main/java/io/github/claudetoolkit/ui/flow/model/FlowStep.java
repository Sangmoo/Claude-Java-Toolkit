package io.github.claudetoolkit.ui.flow.model;

/**
 * 사용자가 읽는 자연어 단계. nodes/edges 와 별도로 1·2·3 순서대로 보여주는
 * 텍스트 시나리오 (사용자 예시 답변의 "1. ... 2. ..." 부분).
 */
public class FlowStep {
    public int    no;
    public String actor;     // "MyBatis Mapper", "Controller" 등
    public String what;      // 자연어 설명
    public String file;      // 관련 파일 (있으면)
    public Integer line;

    public FlowStep() {}
    public FlowStep(int no, String actor, String what) {
        this.no = no; this.actor = actor; this.what = what;
    }

    public int     getNo()    { return no; }
    public String  getActor() { return actor; }
    public String  getWhat()  { return what; }
    public String  getFile()  { return file; }
    public Integer getLine()  { return line; }
}
