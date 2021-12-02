package FolderFastSync;

public class FFSync {
    public static void main(String[] args) {
        if (args.length == 2) {
            String path = args[0];
            String ip = args[1];

            FolderFastSync s = new FolderFastSync(path, ip);
            s.run();
        } else {
            System.out.println("Faltam argumentos referentes ao caminho/ip do servidor");
        }
    }
}
