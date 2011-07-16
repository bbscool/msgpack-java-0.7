package org.msgpack.template.builder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.msgpack.packer.BufferPacker;
import org.msgpack.template.TemplateRegistry;
import org.msgpack.template.Template;
import org.msgpack.testclasses.EnumTypeFieldsClass;
import org.msgpack.testclasses.EnumTypeFieldsClassNotNullable;
import org.msgpack.unpacker.BufferUnpacker;


public class TestReflectionOrdinalEnumBufferPackBufferUnpack extends TestSet {

    @Test @Override
    public void testEnumTypeFieldsClass() throws Exception {
	super.testEnumTypeFieldsClass();
    }

    @Override
    public void testEnumTypeFieldsClass(EnumTypeFieldsClass v) throws Exception {
	TemplateRegistry registry = new TemplateRegistry();
	registry.register(EnumTypeFieldsClass.SampleEnum.class,
		new ReflectionOrdinalEnumTemplateBuilder(registry).buildTemplate(EnumTypeFieldsClass.SampleEnum.class));
	ReflectionTemplateBuilder builder = new ReflectionTemplateBuilder(registry);
	Template<EnumTypeFieldsClass> tmpl = builder.buildTemplate(EnumTypeFieldsClass.class);
	BufferPacker packer = new BufferPacker();
	tmpl.write(packer, v);
	byte[] bytes = packer.toByteArray();
	BufferUnpacker unpacker = new BufferUnpacker();
	unpacker.wrap(bytes);
	EnumTypeFieldsClass ret = tmpl.read(unpacker, null);
	if (v == null) {
	    assertEquals(null, ret);
	    return;
	}
	assertEquals(v, ret);
    }

    @Test @Override
    public void testEnumTypeFieldsClassNotNullable() throws Exception {
	super.testEnumTypeFieldsClassNotNullable();
    }

    @Override
    public void testEnumTypeFieldsClassNotNullable(EnumTypeFieldsClassNotNullable v) throws Exception {
	TemplateRegistry registry = new TemplateRegistry();
	registry.register(EnumTypeFieldsClassNotNullable.SampleEnum.class,
		new ReflectionOrdinalEnumTemplateBuilder(registry).buildTemplate(EnumTypeFieldsClassNotNullable.SampleEnum.class));
	ReflectionTemplateBuilder builder = new ReflectionTemplateBuilder(registry);
	Template<EnumTypeFieldsClassNotNullable> tmpl = builder.buildTemplate(EnumTypeFieldsClassNotNullable.class);
	BufferPacker packer = new BufferPacker();
	tmpl.write(packer, v);
	byte[] bytes = packer.toByteArray();
	BufferUnpacker unpacker = new BufferUnpacker();
	unpacker.wrap(bytes);
	EnumTypeFieldsClassNotNullable ret = tmpl.read(unpacker, null);
	if (v == null) {
	    assertEquals(null, ret);
	    return;
	}
	assertEquals(v, ret);
    }
}