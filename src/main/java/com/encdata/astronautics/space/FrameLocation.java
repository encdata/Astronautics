package com.encdata.astronautics.space;

import net.minecraft.server.level.ServerLevel;

record FrameLocation(ServerLevel level, SpaceGridFrameData.Frame frame) {}
