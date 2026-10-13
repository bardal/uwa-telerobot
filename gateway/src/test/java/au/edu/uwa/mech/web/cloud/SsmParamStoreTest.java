package au.edu.uwa.mech.web.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;
import software.amazon.awssdk.services.ssm.model.PutParameterResponse;

/** SsmParamStore against a fake client: no AWS calls. */
class SsmParamStoreTest {
    final List<GetParameterRequest> gets = new ArrayList<>();
    final List<PutParameterRequest> puts = new ArrayList<>();

    final SsmClient fake = new SsmClient() {
        @Override public String serviceName() { return "ssm"; }
        @Override public void close() { }

        @Override
        public GetParameterResponse getParameter(GetParameterRequest r) {
            gets.add(r);
            if (r.name().equals("/telerobot/missing")) {
                throw ParameterNotFoundException.builder().message("not found").build();
            }
            return GetParameterResponse.builder().parameter(Parameter.builder().name(r.name()).value("v:" + r.name()).build())
                .build();
        }

        @Override
        public PutParameterResponse putParameter(PutParameterRequest r) {
            puts.add(r);
            return PutParameterResponse.builder().version(2L).build();
        }
    };

    @Test
    void getDecryptsAndMissingIsEmpty() {
        SsmParamStore s = new SsmParamStore(fake);
        assertEquals(Optional.of("v:/telerobot/pins/admin"), s.get("/telerobot/pins/admin"));
        assertTrue(gets.get(0).withDecryption(), "SecureStrings are decrypted");
        assertEquals(Optional.empty(), s.get("/telerobot/missing"));
    }

    @Test
    void putOverwritesWithoutAType() {
        new SsmParamStore(fake).put("/telerobot/heartbeat", "{}");
        PutParameterRequest r = puts.get(0);
        assertEquals("/telerobot/heartbeat", r.name());
        assertEquals("{}", r.value());
        assertTrue(r.overwrite());
        assertNull(r.type(), "the parameter already exists as String; the role may not change its type");
    }
}
