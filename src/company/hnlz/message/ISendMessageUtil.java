package company.hnlz.message;

import java.util.List;

/**
 * 
 * @author haoxl
 * @date 2019-07-15
 * @Description 发送短信工具接口
 */

public interface ISendMessageUtil {
	
	void init();
	
	List<Boolean> send(List<String> phoneNumberList, List<String> messageContentList, ICallBack callBack);
	
	void closeResources();
}
