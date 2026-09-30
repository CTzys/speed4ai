package com.speednet.module.bpm.framework.flowable.core.candidate.strategy.user;

import com.speednet.framework.test.core.ut.BaseMockitoUnitTest;
import com.speednet.module.system.api.dept.PostApi;
import com.speednet.module.system.api.user.AdminUserApi;
import com.speednet.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;

import static com.speednet.framework.common.util.collection.CollectionUtils.convertList;
import static com.speednet.framework.common.util.collection.SetUtils.asSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Disabled // TODO SpeedNet：临时注释
public class BpmTaskCandidatePostStrategyTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmTaskCandidatePostStrategy strategy;

    @Mock
    private PostApi postApi;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    public void testCalculateUsers() {
        // 准备参数
        String param = "1,2";
        // mock 方法
        List<AdminUserRespDTO> users = convertList(asSet(11L, 22L),
                id -> new AdminUserRespDTO().setId(id));
        when(adminUserApi.getUserListByPostIds(eq(asSet(1L, 2L)))).thenReturn(users);

        // 调用
        Set<Long> userIds = strategy.calculateUsersByTask(null, param);
        // 断言
        assertEquals(asSet(11L, 22L), userIds);
    }

}
