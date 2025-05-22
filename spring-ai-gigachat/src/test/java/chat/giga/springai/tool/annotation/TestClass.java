package chat.giga.springai.tool.annotation;

public class TestClass {

    @FewShotExample(request = "request1", params = "param1")
    @FewShotExample(request = "request2", params = "param2")
    @FewShotExample(request = "request3", params = "param3")
    @GigaTool(description = "description")
    public void testMethod1() {}

    @FewShotExample(request = "request1", params = "param1")
    @GigaTool(description = "description")
    public void testMethod2() {}
}
