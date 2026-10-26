package xiaozhi.modules.device.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.service.DeviceAddressBookService;
import xiaozhi.modules.device.service.OtaService;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.sys.service.SysUserUtilService;

class DeviceServiceImplTest {

    @Test
    void updateDeviceConnectionInfoUsesProvidedUpdaterWithoutSecurityContext() {
        DeviceDao deviceDao = mock(DeviceDao.class);
        DeviceServiceImpl service = new DeviceServiceImpl(
                deviceDao, mock(SysUserUtilService.class), mock(SysParamsService.class),
                mock(RedisUtils.class), mock(OtaService.class), mock(DeviceAddressBookService.class));

        service.updateDeviceConnectionInfo("agent-1", "device-1", "1.2.3", 42L);

        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(deviceDao).update(isNull(), captor.capture());

        UpdateWrapper wrapper = captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("id");
        assertThat(wrapper.getSqlSet()).contains("last_connected_at", "update_date", "updater", "app_version");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("device-1", 42L, "1.2.3")
                .anyMatch(java.util.Date.class::isInstance);
    }
}
