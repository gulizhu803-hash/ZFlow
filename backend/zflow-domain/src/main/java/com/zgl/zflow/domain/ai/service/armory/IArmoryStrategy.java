package com.zgl.zflow.domain.ai.service.armory;

import com.zgl.zflow.domain.ai.model.entity.ArmoryRequestEntity;

public interface IArmoryStrategy {

    void armory(ArmoryRequestEntity armoryRequestEntity, ArmoryContext armoryContext);

    String getType();

}
