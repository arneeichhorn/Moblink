import socket


def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('', 7777))
    
    while True:
        print(sock.recvfrom(1024))


main()
        
