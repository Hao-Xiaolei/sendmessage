package company.hnlz.message;

import java.util.List;

/**
 * 
 * @author haoxl
 * @date 2019-07-15
 * @Description 发送短信公共方法中的回调函数接口
 */

public interface ICallBack {
	
	void call(List<String> phoneNumberList, List<String> messageContentList, List<Boolean> resultList);

}
