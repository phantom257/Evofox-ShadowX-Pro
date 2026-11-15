import java.util.Scanner;
import org.hid4java.*;

public class Main {
    static final int PACKET_SIZE = 63;
    static final int IDX_LIGHT_MODE = 8;

    static byte[] configPayload = {
        (byte)15,  // 0x0F command
        (byte)174, // 0xAE
        (byte)10,  // 0x0A
        (byte)47,  // 0x2F
        (byte)1, (byte)1, (byte)1, (byte)0,
        (byte)1,   // light_mode (0=Wave, 1=Neon, 2=Flash, 3=YoYo, 4=Uni, 5=Breath, 6=Off)
        (byte)4,   // report_rate + 1 (4 = 1000Hz)
        (byte)5,   // dpi_count (5)
        (byte)1,   // dpi_index + 1 (1 = Stage 1)
        (byte)0x20, (byte)0x03, // DPI 1 = 800
        (byte)0xB0, (byte)0x04, // DPI 2 = 1200
        (byte)0x40, (byte)0x06, // DPI 3 = 1600
        (byte)0x80, (byte)0x0C, // DPI 4 = 3200
        (byte)0x00, (byte)0x19, // DPI 5 = 6400
        (byte)0xE0, (byte)0x2E, // DPI 6 = 12000
        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
        (byte)0,   // scroll_flag
        (byte)0,   // lod_value
        (byte)0,   // sensor_flag
        (byte)0,   // key_respond
        (byte)15,  // sleep_light (15 mins)
        (byte)0,   // highspeed_mode
        (byte)0x11,// wakeup_flag << 4 | move_light_flag
        0,0,0,0,0,0,0,0,0
    };

    public static void main(String[] args) throws Exception {
        HidServicesSpecification spec = new HidServicesSpecification();
        spec.setAutoStart(false);
        HidServices hidServices = HidManager.getHidServices(spec);
        hidServices.start();
        hidServices.scan();

        HidDevice mouse = null;
        boolean isWired = false;

        for (HidDevice device : hidServices.getAttachedHidDevices()) {
            int vid = device.getVendorId() & 0xFFFF;
            if ((vid == 0xa8a4 || vid == 0xa8a5) &&
                (device.getUsagePage() & 0xFFFF) == 0xFF01 &&
                (device.getUsage() & 0xFFFF) == 0x0010) {
                mouse = device;
                isWired = (vid == 0xa8a4);
                break;
            }
        }

        if (mouse == null || !mouse.open()) {
            System.out.println("Could not open EvoFox Shadow X Pro! Check cable/dongle.");
            hidServices.shutdown();
            return;
        }

        System.out.println("Connected (" + (isWired ? "Wired Mode" : "2.4GHz Wireless Mode") + ")");

        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("\n=== Options ===");
            System.out.println("1. Wave");
            System.out.println("2. Neon");
            System.out.println("3. Touring Flash");
            System.out.println("4. YoYo Ball");
            System.out.println("5. Unidirectional Flashing");
            System.out.println("6. Circular Breathing");
            System.out.println("7. Turn Off Lights");
            if (isWired) {
                System.out.println("8. Freeze Current Color (Static)");
            }
            System.out.println("0. Exit");
            System.out.print("Pick a mode: ");

            if (!sc.hasNextInt()) {
                sc.next();
                continue;
            }

            int choice = sc.nextInt();
            if (choice >= 1 && choice <= 7) {
                configPayload[IDX_LIGHT_MODE] = (byte) (choice - 1);
                applyInstant(mouse);
                System.out.println("Applied preset #" + choice + "!");
            } else if (choice == 8 && isWired) {
                runStaticFreeze(mouse, sc);
            } else if (choice == 0) {
                running = false;
            } else {
                System.out.println("Invalid choice.");
            }
        }

        mouse.close();
        hidServices.shutdown();
        sc.close();
        System.out.println("Exiting.");
    }

    static void applyInstant(HidDevice mouse) {
        mouse.write(configPayload, configPayload.length, (byte) 0xF0);
        byte[] drain = new byte[PACKET_SIZE + 1];
        while (mouse.read(drain, 20) > 0) {}
    }

    static void runStaticFreeze(HidDevice mouse, Scanner sc) throws Exception {
        byte[] freezePayload = {
            (byte)0x0F, (byte)0xAE, (byte)0x0A, (byte)0x2F, (byte)0x01, (byte)0x01, (byte)0x01,
            (byte)0x00, (byte)0x01, (byte)0x04, (byte)0x05, (byte)0x00, (byte)0x20, (byte)0x03,
            (byte)0xB0, (byte)0x04, (byte)0x40, (byte)0x06, (byte)0x80, (byte)0x0C,
            (byte)0x00, (byte)0x19, (byte)0xE0, (byte)0x2E,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            (byte)0x0F, (byte)0x00, (byte)0x11,
            0,0,0,0,0,0,0,0,0
        };

        sc.nextLine();
        System.out.println("\nPress Enter to unfreeze...");
        System.out.println("(Note: The physical DPI button may not work while frozen.)");

        while (System.in.available() == 0) {
            mouse.write(freezePayload, freezePayload.length, (byte) 0xF0);
            Thread.sleep(900);
        }

        sc.nextLine();
        applyInstant(mouse);
        System.out.println("Unfrozen!");
    }
}
