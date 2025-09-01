package jpassport;

import java.lang.foreign.*;
import java.util.HashMap;
import java.util.Optional;

public final class ErrorCapture {

    private final HashMap<String, Integer> errMap = new HashMap<>();

    public MemorySegment alloc(Arena arena)
    {
        var stateLayout = Linker.Option.captureStateLayout();
        return arena.allocate(stateLayout);
    }

    public void readAfter(MemorySegment segment)
    {
        var stateLayout = Linker.Option.captureStateLayout();
        for (var mem : stateLayout.memberLayouts())
        {
            var name = mem.name();
            if (name.isPresent())
            {
                int errNo = segment.get(ValueLayout.JAVA_INT, stateLayout.byteOffset(MemoryLayout.PathElement.groupElement(name.get())));
                errMap.put(name.get(), errNo);
            }
        }
    }

    public int getError(String name)
    {
        return errMap.getOrDefault(name, 0);
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder("Errors:");
        for (String key : errMap.keySet())
        {
            sb.append(key).append("=").append(errMap.get(key)).append(",");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    static String[] getErrNames()
    {
        var stateLayout = Linker.Option.captureStateLayout();

        var names = stateLayout.memberLayouts().stream().map(MemoryLayout::name)
                .filter(Optional::isPresent).map(Optional::get).toList();
        return names.toArray(new String[0]);
    }
}
